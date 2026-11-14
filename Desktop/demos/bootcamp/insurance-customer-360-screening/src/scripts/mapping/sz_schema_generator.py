#! /usr/bin/env python3
"""Analyze source data files and generate schema documentation.

Supports CSV, JSON, JSONL, and Parquet formats. Generates markdown
reports with field statistics, data types, and sample values.
"""
import argparse
import csv
import glob
import io
import json
import pathlib
import sys
import xml.etree.ElementTree as ET


try:
    import numpy as np
except ImportError:
    np = None

try:
    import pandas as pd
except ImportError:
    pd = None

try:
    import openpyxl
    HAS_OPENPYXL = True
except ImportError:
    HAS_OPENPYXL = False


# --- File Readers ---

class FileReader:
    """Base class for file readers."""

    def __init__(self, file_path, encoding="utf-8"):
        self.file_path = file_path
        self.encoding = encoding
        self._file_handle = None

    def __enter__(self):
        self.open()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
        return False

    def open(self):
        pass

    def close(self):
        if self._file_handle:
            self._file_handle.close()
            self._file_handle = None


def _looks_headerless(rows):
    """Heuristic: does the first row look like DATA rather than column names?

    A header row is normally all-text and distinct from the rows beneath it. If the first
    row's cells have the same *shape* as the rows below (numeric where they are numeric,
    date-like where they are date-like), it is almost certainly data.

    Conservative by design: only reports True on positive evidence, because guessing wrong
    in this direction discards a real header. Needs at least one comparison row.
    """
    if len(rows) < 3:
        return False
    first, rest = rows[0], rows[1:]
    width = len(first)
    if width == 0 or any(len(r) != width for r in rest):
        return False

    def shape(cell):
        c = (cell or "").strip()
        if not c:
            return "empty"
        try:
            float(c.replace(",", ""))
            return "numeric"
        except ValueError:
            pass
        # crude date-ish: digits with separators, e.g. 1/2/18 or 2018-01-02
        if any(sep in c for sep in ("/", "-")) and any(ch.isdigit() for ch in c):
            return "datish"
        return "text"

    # A duplicate value in the first row cannot be a header (column names are unique).
    non_empty = [c.strip() for c in first if (c or "").strip()]
    if len(set(non_empty)) != len(non_empty):
        return True

    matches = 0
    comparable = 0
    for col in range(width):
        below = [shape(r[col]) for r in rest if shape(r[col]) != "empty"]
        if not below:
            continue
        # Only informative for non-text columns; a text header over text data is normal.
        dominant = max(set(below), key=below.count)
        if dominant == "text":
            continue
        comparable += 1
        if shape(first[col]) == dominant:
            matches += 1
    return comparable > 0 and matches == comparable


class CSVReader(FileReader):
    """Reader for CSV files with automatic dialect detection.

    Supports headerless sources. Previously the first data row was always consumed as
    column names, which silently lost one record AND mislabelled every column with a value
    from that row — producing a confident, wrong profile rather than an error. Reported
    2026-07-31 against a headerless source with 12 documented positional columns.
    """

    def __init__(self, file_path, encoding="utf-8", no_header=None, column_names=None):
        super().__init__(file_path, encoding)
        # None = autodetect, True/False = forced by the caller.
        self.no_header = no_header
        self.column_names = column_names
        self.headerless_detected = False

    def open(self):
        self._file_handle = open(self.file_path, "r", encoding=self.encoding)
        sample = self._file_handle.read(8192)
        self._file_handle.seek(0)
        try:
            dialect = csv.Sniffer().sniff(sample, delimiters=[",", ";", "|", "\t"])
        except csv.Error:
            dialect = None

        no_header = self.no_header
        if no_header is None:
            probe = csv.reader(io.StringIO(sample), dialect) if dialect \
                else csv.reader(io.StringIO(sample), delimiter="\t")
            # Drop a trailing partial row from the 8 KiB cut.
            probe_rows = list(probe)[:-1]
            no_header = _looks_headerless(probe_rows)
            self.headerless_detected = no_header
            if no_header:
                print(
                    f"  NOTE: '{self.file_path}' looks headerless (first row has the same "
                    f"value shapes as the rows below). Profiling with positional column "
                    f"names col_001..col_NNN so no record is lost. Pass --header to override, "
                    f"or --column-names a,b,c to supply real names.",
                    file=sys.stderr,
                )

        fieldnames = None
        if no_header:
            fieldnames = self.column_names
            if not fieldnames:
                probe = csv.reader(io.StringIO(sample), dialect) if dialect \
                    else csv.reader(io.StringIO(sample), delimiter="\t")
                first = next(iter(probe), [])
                fieldnames = [f"col_{i + 1:03d}" for i in range(len(first))]

        if dialect:
            self._reader = csv.DictReader(
                self._file_handle, dialect=dialect, fieldnames=fieldnames
            )
        else:
            self._reader = csv.DictReader(
                self._file_handle, delimiter="\t", fieldnames=fieldnames
            )

    def __iter__(self):
        return iter(self._reader)


class JSONReader(FileReader):
    """Reader for JSON array files, objects with nested data arrays, and Socrata format."""

    def __init__(self, file_path, encoding="utf-8"):
        super().__init__(file_path, encoding)
        self.is_socrata = False
        self.field_metadata = {}

    def open(self):
        self._file_handle = open(self.file_path, "r", encoding=self.encoding)
        try:
            self._data = json.load(self._file_handle)
        except json.JSONDecodeError:
            # Not valid JSON — check if it's actually JSONL (one JSON object per line)
            self._file_handle.seek(0)
            first_line = ""
            for line in self._file_handle:
                first_line = line.strip()
                if first_line:
                    break
            if first_line:
                try:
                    json.loads(first_line)
                    # First line is valid JSON — switch to JSONL mode
                    print(f"NOTE: {self.file_path} has .json extension but contains "
                          f"JSON Lines data — reading as JSONL", file=sys.stderr)
                    self._file_handle.seek(0)
                    self._is_jsonl = True
                    return
                except json.JSONDecodeError:
                    pass
            raise ValueError(
                f"Failed to parse {self.file_path} as JSON or JSONL")

        self._is_jsonl = False

        if isinstance(self._data, list):
            return
        elif isinstance(self._data, dict):
            # Check for Socrata format (meta.view.columns + data)
            if (isinstance(self._data.get('meta'), dict) and
                isinstance(self._data['meta'].get('view'), dict) and
                'columns' in self._data['meta']['view'] and
                'data' in self._data):
                columns = self._data['meta']['view']['columns']
                field_names = []
                for col in columns:
                    fn = col.get('fieldName', col.get('name', f'col_{col["position"]}'))
                    field_names.append(fn)
                    self.field_metadata[fn] = {
                        'description': col.get('description', ''),
                        'dataTypeName': col.get('dataTypeName', ''),
                        'name': col.get('name', '')
                    }
                    cc = col.get('cachedContents', {})
                    if 'cardinality' in cc:
                        try:
                            self.field_metadata[fn]['cardinality'] = int(cc['cardinality'])
                        except (ValueError, TypeError):
                            pass
                self.is_socrata = True
                self._data = [dict(zip(field_names, row)) for row in self._data['data']]
                return

            for field in ['data', 'rows', 'items', 'records', 'results']:
                if field in self._data and isinstance(self._data[field], list):
                    self._data = self._data[field]
                    break
            else:
                raise ValueError(
                    "JSON file must contain an array at root level or have a "
                    "'data', 'rows', 'items', 'records', or 'results' field containing an array"
                )
        else:
            raise ValueError("JSON file must contain an array or object at root level")

    def __iter__(self):
        if self._is_jsonl:
            # Fallback JSONL mode — read line by line
            line_number = 0
            for line in self._file_handle:
                line_number += 1
                line = line.strip()
                if not line:
                    continue
                try:
                    yield json.loads(line)
                except json.JSONDecodeError as e:
                    yield ParseError(f"Invalid JSON on line {line_number}: {e}")
        else:
            yield from self._data


class ParseError:
    """Sentinel for parse errors during iteration."""
    def __init__(self, message):
        self.message = message
    def __str__(self):
        return self.message


class JSONLReader(FileReader):
    """Reader for JSONL (JSON Lines) files."""

    def open(self):
        self._file_handle = open(self.file_path, "r", encoding=self.encoding)
        self._line_number = 0

    def __iter__(self):
        for line in self._file_handle:
            self._line_number += 1
            line = line.strip()
            if not line:
                continue
            try:
                yield json.loads(line)
            except json.JSONDecodeError as e:
                yield ParseError(f"Invalid JSON on line {self._line_number}: {e}")


class ParquetReader(FileReader):
    """Reader for Parquet files."""

    def open(self):
        if pd is None:
            raise ImportError("Pandas must be installed to read parquet files")
        self._records = pd.read_parquet(self.file_path, engine="auto").to_dict(orient="records")

    def __iter__(self):
        return iter(self._records)

    def close(self):
        pass


class XMLReader(FileReader):
    """Reader for XML files."""

    def __init__(self, file_path, encoding='utf-8'):
        super().__init__(file_path, encoding)
        self.namespaces = {}

    def open(self):
        tree = ET.parse(self.file_path)
        root = tree.getroot()

        # Extract namespaces from root element
        if root.tag.startswith('{'):
            default_ns = root.tag.split('}')[0][1:]
            self.namespaces['default'] = default_ns

        for key, value in root.attrib.items():
            if key == 'xmlns':
                self.namespaces['default'] = value
            elif key.startswith('{http://www.w3.org/2000/xmlns/}') or key.startswith('xmlns:'):
                prefix = key.split('}')[-1] if '}' in key else key.split(':')[-1]
                self.namespaces[prefix] = value

        self._elements = [self._element_to_dict(child) for child in root]

    def __iter__(self):
        return iter(self._elements)

    def _element_to_dict(self, element):
        """Convert XML element to dictionary."""
        result = {}
        if element.attrib:
            result.update({self._strip_namespace(k): v for k, v in element.attrib.items()})
        if element.text and element.text.strip():
            result['text'] = element.text.strip()

        children = {}
        for child in element:
            child_dict = self._element_to_dict(child)
            tag = self._strip_namespace(child.tag)
            if tag in children:
                if not isinstance(children[tag], list):
                    children[tag] = [children[tag]]
                children[tag].append(child_dict)
            else:
                children[tag] = child_dict

        result.update(children)
        return result

    def _strip_namespace(self, tag):
        """Remove XML namespace from tag."""
        if isinstance(tag, str) and tag.startswith('{'):
            return tag.split('}', 1)[1]
        return tag

    def close(self):
        pass


class XLSXReader(FileReader):
    """Reader for Excel .xlsx files using openpyxl."""

    def open(self):
        if not HAS_OPENPYXL:
            raise ImportError("xlsx files require openpyxl: pip install openpyxl")
        wb = openpyxl.load_workbook(self.file_path, read_only=True, data_only=True)
        ws = wb.active
        rows = ws.iter_rows(values_only=True)
        self._headers = [str(h) if h is not None else f"col_{i}" for i, h in enumerate(next(rows))]
        self._rows = rows
        self._workbook = wb

    def __iter__(self):
        for row in self._rows:
            yield dict(zip(self._headers, (str(v) if v is not None else "" for v in row)))

    def close(self):
        if hasattr(self, '_workbook') and self._workbook:
            self._workbook.close()
            self._workbook = None


def get_reader(file_type, file_path, encoding="utf-8", no_header=None,
               column_names=None):
    """Factory function to get appropriate reader for file type.

    `no_header` / `column_names` apply to CSV only; other formats are self-describing.
    """
    readers = {
        'csv': CSVReader, 'json': JSONReader, 'jsonl': JSONLReader,
        'xml': XMLReader, 'xmls': XMLReader, 'parquet': ParquetReader,
        'xlsx': XLSXReader,
    }
    reader_class = readers.get(file_type.lower())
    if not reader_class:
        raise ValueError(f"Unsupported file type: {file_type}")
    if reader_class is CSVReader:
        return CSVReader(file_path, encoding, no_header=no_header,
                         column_names=column_names)
    return reader_class(file_path, encoding)


# --- Node ---

class Node:
    """Represents a node in the data schema tree."""

    def __init__(self, node_id):
        self.node_desc = node_id
        self.node_type = None
        self.children = []
        self.record_count = 0
        self.unique_values = {}
        self.table_context = None
        self.table_record_count = 0


# --- File Analyzer ---

_STRUCT_DETECT_LIMIT = 50_000
_STRUCT_MAX_PATTERNS = 500


class FileAnalyzer:
    """Analyzes file structure, collects field statistics, and generates markdown reports."""

    def __init__(self, file_type, group_by_attr=None):
        self.record_count = 0
        self.file_type = file_type
        self.default_schema_name = "data"
        self.schemas = {}
        self.top_value_count = 10
        self.group_by_attr = group_by_attr
        self.group_by_filter = None
        self.field_metadata = {}
        # Structure detection
        self._struct_fingerprints = {}  # frozenset → count
        self._struct_n = 0
        self._struct_capped = False

    def _leaf_fingerprint(self, obj):
        """Depth-2 leaf fingerprint: root scalars + one level into dict children."""
        keys = set()
        for k, v in obj.items():
            if isinstance(v, dict):
                for k2 in v:
                    keys.add(f"{k}.{k2}")
            else:
                keys.add(k)
        return frozenset(keys)

    def _ensure_schema(self, schema_key):
        if schema_key not in self.schemas:
            root = Node("root")
            root.node_desc = schema_key
            root.node_type = self.file_type
            self.schemas[schema_key] = {"nodes": {"root": root}, "record_count": 0}

    def process_record(self, obj):
        self.record_count += 1
        if (isinstance(obj, dict) and not self.group_by_attr
                and self._struct_n < _STRUCT_DETECT_LIMIT
                and not self._struct_capped):
            self._struct_n += 1
            fp = self._leaf_fingerprint(obj)
            if fp in self._struct_fingerprints:
                self._struct_fingerprints[fp] += 1
            elif len(self._struct_fingerprints) < _STRUCT_MAX_PATTERNS:
                self._struct_fingerprints[fp] = 1
            else:
                self._struct_capped = True
        if self.group_by_attr and self.group_by_filter and isinstance(obj, dict):
            if str(obj.get(self.group_by_attr, "unknown")) != str(self.group_by_filter):
                return
        if self.group_by_attr and isinstance(obj, dict):
            schema_key = str(obj.get(self.group_by_attr, "unknown"))
        else:
            schema_key = self.default_schema_name
        self._ensure_schema(schema_key)
        self.schemas[schema_key]["record_count"] += 1
        self._iterate_obj(schema_key, "root", obj)

    def _iterate_obj(self, schema_key, prior_key, obj):
        if isinstance(obj, dict):
            for key, value in obj.items():
                if key and key != self.group_by_attr:
                    self._update_node(schema_key, prior_key, key, value)
                    check_types = (dict, list, np.ndarray) if np is not None else (dict, list)
                    if isinstance(value, check_types):
                        self._iterate_obj(schema_key, f"{prior_key}.{key}", value)
        elif isinstance(obj, list):
            for item in obj:
                check_types = (dict, list, np.ndarray) if np else (dict, list)
                if isinstance(item, check_types):
                    self._iterate_obj(schema_key, prior_key, item)
                else:
                    self._update_node(schema_key, prior_key, "", item)

    def _update_node(self, schema_key, prior_key, key, value):
        attr_key = f"{prior_key}.{key}" if key else prior_key
        nodes = self.schemas[schema_key]["nodes"]
        if attr_key not in nodes:
            nodes[attr_key] = Node(attr_key)
            nodes[attr_key].node_desc = attr_key.replace("root.", "")
            nodes[attr_key].node_type = "unk"
            nodes[prior_key].children.append(nodes[attr_key])
        if value is not None:
            try:
                is_empty = len(value) == 0 if hasattr(value, '__len__') and not isinstance(value, str) else not bool(value)
            except (ValueError, TypeError):
                is_empty = False
            if not is_empty:
                value_type = type(value).__name__
                if nodes[attr_key].node_type == "unk":
                    nodes[attr_key].node_type = value_type
                elif nodes[attr_key].node_type == "dict" and value_type == "list":
                    nodes[attr_key].node_type = "list"
                if isinstance(value, (dict, list)):
                    value = f"{len(value)} items"
                elif np is not None and isinstance(value, np.ndarray):
                    value = f"array({value.shape}) items"
                value = str(value)
                nodes[attr_key].record_count += 1
                nodes[attr_key].unique_values[value] = nodes[attr_key].unique_values.get(value, 0) + 1

    def calculate_table_contexts(self):
        for schema_data in self.schemas.values():
            nodes = schema_data["nodes"]
            schema_count = schema_data["record_count"]
            list_nodes = {p: n for p, n in nodes.items() if n.node_type == 'list'}
            for node_path, node in nodes.items():
                if node_path == "root":
                    continue
                path_parts = node_path.split('.')
                table_context_path = None
                table_count = schema_count
                for i in range(len(path_parts) - 1, 0, -1):
                    ancestor = '.'.join(path_parts[:i])
                    if ancestor in list_nodes:
                        table_context_path = ancestor
                        for cp, cn in nodes.items():
                            if cp.startswith(ancestor + '.') and '.' not in cp[len(ancestor) + 1:]:
                                table_count = cn.record_count
                                break
                        break
                node.table_context = table_context_path
                node.table_record_count = table_count

    def matches_filter(self, obj, filter_attr, filter_value):
        current = obj
        for part in filter_attr.split("."):
            if isinstance(current, dict) and part in current:
                current = current[part]
            else:
                return False
        return str(current) == filter_value

    def _traverse_nodes(self, nodes, root_node):
        parents = [{"node": root_node, "children": root_node.children.copy()}]
        while parents:
            if not parents[-1]["children"]:
                parents.pop()
                continue
            next_node = parents[-1]["children"].pop(0)
            yield next_node
            if next_node.children:
                parents.append({"node": next_node, "children": next_node.children.copy()})

    def _generate_schema(self, lines, schema_name, schema_data):
        nodes = schema_data["nodes"]
        record_count = schema_data["record_count"]
        root_node = nodes["root"]
        field_count = len(nodes) - 1
        list_count = sum(1 for n in nodes.values() if n.node_type == 'list')
        n = self.top_value_count

        lines.append(f"## Schema: {schema_name}")
        lines.append("")
        lines.append(f"**Record Count:** {record_count}")
        if list_count > 0:
            lines.append(f"**List Elements (Tables):** {list_count}")
        lines.append(f"**Field Count:** {field_count}")
        lines.append("")

        # List hierarchy
        list_nodes_info = {}
        for node_path, node in nodes.items():
            if node.node_type == 'list' and node_path.startswith('root.'):
                for cp, cn in nodes.items():
                    if cp.startswith(node_path + '.') and '.' not in cp[len(node_path) + 1:]:
                        list_nodes_info[node_path.replace('root.', '')] = cn.record_count
                        break
        if list_nodes_info:
            list_info = {}
            for name, count in list_nodes_info.items():
                parent = None
                if '.' in name:
                    parts = name.split('.')
                    for i in range(len(parts) - 1, 0, -1):
                        p = '.'.join(parts[:i])
                        if p in list_nodes_info:
                            parent = p
                            break
                list_info[name] = (count, parent)
            lines.append("**Data Elements (All Lists):**")
            def show_hierarchy(name, indent=0):
                ct, _ = list_info[name]
                lines.append(f"{'  ' * indent}- {name}: {ct:,} records")
                for cn, (cc, cp) in sorted(list_info.items(), key=lambda x: -x[1][0]):
                    if cp == name:
                        show_hierarchy(cn, indent + 1)
            for name, _ in sorted([(nm, ct) for nm, (ct, pr) in list_info.items() if pr is None], key=lambda x: -x[1]):
                show_hierarchy(name)
            lines.append("")

        # Field table
        lines.append("### Fields")
        lines.append("")
        lines.append("| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context |" + "".join(f" Sample {i+1} |" for i in range(n)))
        lines.append("|---|------------|------|---------|-------|--------|----------|---------------|" + "----------|" * n)

        row_num = 0
        for node in self._traverse_nodes(nodes, root_node):
            row_num += 1
            ft = node.node_type
            rc = node.record_count
            if ft in ('dict', 'list'):
                pop, uc, up = "", "", ""
            else:
                tc = node.table_record_count if node.table_record_count > 0 else record_count
                pop = f"{round(rc / tc * 100, 1)}%" if tc else "0%"
                uc = len(node.unique_values)
                up = f"{round(uc / rc * 100, 1)}%" if rc else "0%"
            top = [f"{str(k)[:50]} ({v})" for k, v in sorted(node.unique_values.items(), key=lambda x: -x[1])[:n]]
            top += [""] * (n - len(top))
            ctx = (node.table_context.split('.')[-1] + f" ({node.table_record_count})") if node.table_context else f"root ({record_count})"
            lines.append(f"| {row_num} | {node.node_desc} | {ft} | {rc} | {pop} | {uc} | {up} | {ctx} |" + "".join(f" {v} |" for v in top))
            if node.node_desc in self.field_metadata:
                desc = self.field_metadata[node.node_desc].get('description', '')
                if desc:
                    lines.append(f"| | *{desc}* | | | | | | | |")
        lines.append("")

    def _generate_structure_variations(self):
        """Report structure variations if multiple field patterns found."""
        if self.group_by_attr or len(self._struct_fingerprints) <= 1:
            return []
        patterns = sorted(self._struct_fingerprints.items(), key=lambda x: -x[1])
        common = set.intersection(*(set(fp) for fp, _ in patterns))
        total = sum(c for _, c in patterns)
        lines = []
        lines.append("## Structure Variations")
        lines.append("")
        lines.append(f"**Distinct field patterns:** {len(patterns)} "
                     f"(across {total:,} records)")
        if common:
            lines.append(f"**Common fields:** {', '.join(sorted(common))}")
        lines.append("**Hint:** A 100% populated field with low cardinality "
                     "(few unique values) likely indicates record type.")
        lines.append("")
        lines.append("| # | Records | Fields unique to this pattern |")
        lines.append("|---|---------|-------------------------------|")
        for i, (fp, count) in enumerate(patterns[:10], 1):
            unique = sorted(set(fp) - common)
            pct = round(count / total * 100)
            unique_str = ", ".join(unique[:8])
            if len(unique) > 8:
                unique_str += f", ... ({len(unique)} total)"
            elif not unique:
                unique_str = "— (only common fields)"
            lines.append(f"| {i} | {count:,} ({pct}%) | {unique_str} |")
        if len(patterns) > 10:
            rest = sum(c for _, c in patterns[10:])
            lines.append(f"| ... | {rest:,} ({round(rest / total * 100)}%) | "
                         f"({len(patterns) - 10} more patterns) |")
        lines.append("")
        lines.append("To profile each structure separately, "
                     "re-run with: `--group_by <field>`")
        lines.append("")
        return lines

    def generate_markdown_report(self):
        lines = []
        total_fields = sum(len(s["nodes"]) - 1 for s in self.schemas.values())
        lines.append(f"**File Type:** {self.file_type}")
        lines.append(f"**Root Records:** {self.record_count}")
        if len(self.schemas) > 1:
            lines.append(f"**Total Schemas:** {len(self.schemas)}")
        lines.append(f"**Total Fields:** {total_fields}")
        lines.append("")
        struct_lines = self._generate_structure_variations()
        if struct_lines:
            lines.extend(struct_lines)
        for schema_name in sorted(self.schemas.keys()):
            self._generate_schema(lines, schema_name, self.schemas[schema_name])
        return "\n".join(lines)



# --- Helpers ---

SUPPORTED_EXTENSIONS = {".csv", ".json", ".jsonl", ".xml", ".xmls", ".parquet", ".xlsx"}


def _detect_type(path):
    ext = pathlib.Path(path).suffix.lower()
    if ext not in SUPPORTED_EXTENSIONS:
        if ext:
            raise ValueError(f"Unsupported file type: '{ext[1:]}'. "
                             f"Supported types: csv, json, jsonl, xml, parquet")
        return "csv"  # No extension → try CSV
    if ext == ".xmls":
        return "xml"
    return ext[1:]


def self_test():
    """Run self-test with embedded sample data."""
    import os
    import tempfile
    test_csv = "id,name,email,phone\n1,John Smith,john@test.com,555-1234\n2,Jane Doe,jane@test.com,555-5678\n"
    with tempfile.NamedTemporaryFile(mode='w', suffix='.csv', delete=False) as f:
        f.write(test_csv)
        tmp_path = f.name
    try:
        analyzer = FileAnalyzer("csv")
        with get_reader("csv", tmp_path) as reader:
            for row in reader:
                analyzer.process_record(row)
        analyzer.calculate_table_contexts()
        report = analyzer.generate_markdown_report()
        assert "id" in report and "name" in report and "email" in report, "Missing expected fields in report"
        assert "Record Count" in report, "Missing Record Count in report"
        print("PASSED - profiler is functional")
        return 0
    except Exception as e:
        print(f"FAILED - {e}", file=sys.stderr)
        return 1
    finally:
        os.unlink(tmp_path)


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "--self-test":
        sys.exit(self_test())

    parser = argparse.ArgumentParser(
        description="Analyze file structure and generate markdown schema reports.",
        epilog="EXAMPLES:\n  %(prog)s data.csv -o schema.md\n  %(prog)s data.jsonl --group_by schema -o schema.md\n  %(prog)s data.jsonl --filter 'status=active' -o schema.md",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("input_file", help="Input file or directory path")
    parser.add_argument("-t", "--file_type", help="File type (auto-detected if not specified)")
    parser.add_argument("-e", "--encoding", default="utf-8", help="File encoding (default: utf-8)")
    parser.add_argument("-o", "--output_file", help="Output file path")
    parser.add_argument("--top_values", type=int, default=5, help="Number of top values to display (default: 5)")
    parser.add_argument("--max_records", type=int, default=1_000_000,
                        help="Max records to read per file (default: 1,000,000). 0 = no limit.")
    parser.add_argument("--filter", help="Filter records: 'attribute=value'")
    header_group = parser.add_mutually_exclusive_group()
    header_group.add_argument(
        "--no-header", dest="no_header", action="store_const", const=True, default=None,
        help="CSV has no header row; columns are profiled positionally as col_001..col_NNN. "
             "Without this the first data row is consumed as column names, losing that "
             "record and mislabelling every column. Default: autodetect.")
    header_group.add_argument(
        "--header", dest="no_header", action="store_const", const=False,
        help="Force the first CSV row to be treated as a header, overriding autodetection.")
    parser.add_argument(
        "--column-names", dest="column_names",
        help="Comma-separated column names to use with --no-header (positional order).")
    parser.add_argument("--group_by", help="Group analysis by attribute: 'attr' or 'attr=value'")
    args = parser.parse_args()

    # Resolve file list
    if pathlib.Path(args.input_file).is_dir():
        file_list = []
        for ext in ('.csv', '.json', '.jsonl', '.xml', '.parquet', '.xlsx'):
            file_list.extend(glob.glob(str(pathlib.Path(args.input_file) / f'*{ext}')))
        if not file_list:
            print(f"\nNo data files found in directory: {args.input_file}\n")
            sys.exit(1)
        file_list.sort()
        print(f"\nFound {len(file_list)} file(s) in directory {args.input_file}\n")
    else:
        file_list = glob.glob(args.input_file)
        if not file_list:
            print("\nPlease supply a valid input file specification on the command line\n")
            sys.exit(1)

    args.file_type = args.file_type.lower() if args.file_type else _detect_type(file_list[0])

    if args.file_type == "parquet" and pd is None:
        print("\nPandas must be installed to analyze parquet files, try: pip3 install pandas\n")
        sys.exit(1)

    if args.file_type == "xlsx" and not HAS_OPENPYXL:
        print("\nxlsx files require openpyxl: pip install openpyxl\n")
        sys.exit(1)

    # Parse group_by
    group_by_attr = group_by_filter = None
    if args.group_by:
        if '=' in args.group_by:
            group_by_attr, group_by_filter = args.group_by.split('=', 1)
        else:
            group_by_attr = args.group_by

    analyzer = FileAnalyzer(args.file_type, group_by_attr)
    analyzer.top_value_count = args.top_values
    if group_by_filter:
        analyzer.group_by_filter = group_by_filter

    filter_attr = filter_value = None
    if args.filter:
        filter_attr, filter_value = args.filter.split("=")

    auto_group_by_file = len(file_list) > 1 and not group_by_attr
    if auto_group_by_file:
        analyzer.group_by_attr = "_source_file"
        print(f"Auto-grouping by source file (processing {len(file_list)} files)\n")
    elif len(file_list) == 1 and not group_by_attr:
        analyzer.default_schema_name = pathlib.Path(file_list[0]).stem

    # Process files
    MAX_ERRORS, ERR_PCT, MIN_REC, MAX_CONSEC = 10, 10, 100, 10
    error_count = consecutive_errors = shut_down = 0
    capped_files = []
    max_rec = args.max_records if args.max_records > 0 else None

    try:
        for file_num, file_name in enumerate(file_list, 1):
            print(f"reading file {file_num} of {len(file_list)}: {file_name}", file=sys.stderr)
            schema_name = pathlib.Path(file_name).stem
            reader = get_reader(
                args.file_type, file_name, args.encoding,
                no_header=args.no_header,
                column_names=(
                    [c.strip() for c in args.column_names.split(',')]
                    if getattr(args, 'column_names', None) else None
                ),
            )
            file_record_count = 0

            with reader:
                if hasattr(reader, 'is_socrata') and reader.is_socrata:
                    analyzer.file_type = 'socrata-json'
                    analyzer.default_schema_name = 'data'
                    if hasattr(reader, 'field_metadata'):
                        analyzer.field_metadata = reader.field_metadata

                for row in reader:
                    if isinstance(row, ParseError):
                        error_count += 1
                        consecutive_errors += 1
                        if error_count <= MAX_ERRORS:
                            print(f"ERROR: {row}", file=sys.stderr)
                        elif error_count == MAX_ERRORS + 1:
                            print("(additional errors suppressed)", file=sys.stderr)
                        if consecutive_errors >= MAX_CONSEC:
                            print(f"\nABORTED: {MAX_CONSEC} consecutive parse errors", file=sys.stderr)
                            shut_down = 1
                            break
                        total = analyzer.record_count + error_count
                        if total >= MIN_REC and (error_count / total) * 100 >= ERR_PCT:
                            print(f"\nABORTED: Error rate exceeds {ERR_PCT}% threshold", file=sys.stderr)
                            shut_down = 1
                            break
                        continue

                    consecutive_errors = 0
                    if auto_group_by_file and isinstance(row, dict):
                        row["_source_file"] = schema_name
                    if args.filter and not analyzer.matches_filter(row, filter_attr, filter_value):
                        continue
                    analyzer.process_record(row)
                    file_record_count += 1
                    if analyzer.record_count % 10000 == 0:
                        print(f"{analyzer.record_count:,} rows read", file=sys.stderr)
                    if max_rec and file_record_count >= max_rec:
                        print(f"\nCAP REACHED: {max_rec:,} records read from {file_name} — "
                              f"remaining records skipped. Profile is based on a sample.",
                              file=sys.stderr)
                        capped_files.append(file_name)
                        break

                if shut_down:
                    break

    except KeyboardInterrupt:
        shut_down = 9

    if shut_down == 1:
        status = "aborted"
    elif shut_down > 1:
        status = "interrupted"
    elif capped_files:
        status = "capped"
    else:
        status = "complete"
    print(f"\n{analyzer.record_count:,} rows read, file {status}", file=sys.stderr)
    if error_count > 0:
        print(f"{error_count:,} records skipped due to parse errors", file=sys.stderr)
    if capped_files:
        print(f"{len(capped_files)} file(s) capped at {max_rec:,} records", file=sys.stderr)

    analyzer.calculate_table_contexts()
    markdown_content = analyzer.generate_markdown_report()
    if capped_files:
        cap_note = (f"\n**NOTE: The following file(s) were capped at {max_rec:,} records. "
                    f"The profile is based on a sample and may not represent all data patterns:**\n")
        for cf in capped_files:
            cap_note += f"- {pathlib.Path(cf).name} (capped at {max_rec:,})\n"
        markdown_content = cap_note + "\n" + markdown_content
    if args.output_file:
        with open(args.output_file, "w") as f:
            f.write(markdown_content)
        print(f"schema saved to {args.output_file}\n", file=sys.stderr)
    else:
        print(markdown_content)

    sys.exit(shut_down)
