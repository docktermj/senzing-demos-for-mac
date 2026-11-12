#! /usr/bin/env python3
"""Shared production loader for Senzing JSONL files (one record per line).

Foundation: senzing/code-snippets-v4 python/loading/add_futures.py, served by the Senzing MCP server
(sdk_guide topic='load', threaded/production pattern; copy kept at
src/resources/scaffold/loading/add_futures.py). The SDK calls are unchanged from the snippet:
SzAbstractFactoryCore(...), factory.create_engine(), engine.add_record(data_source, record_id, record).
Added around them: per-record error log, throughput progress, final statistics, checkpoint/resume.

ARCHITECTURE (production volume tier: MEDIUM, 500,000 - 10,000,000 records; chosen as a range in
Data processing step 1, no exact count given):
  * Thread-pooled loading (concurrent.futures), one factory and one engine per process shared by
    all workers -- Senzing's guidance: do not initialize the factory per call or per thread.
  * Rolling window of in-flight records (the "batch"), checkpoint/resume, throughput reporting.

CONCURRENCY -- READ THIS BEFORE MOVING TO PRODUCTION:
  MAX_WORKERS is 1 because this bootcamp's datastore is SQLite. Senzing's guidance says SQLite
  "does not support concurrent writes" (database-locked errors under concurrent access) and to
  "tune based on your database and storage throughput". A single writer is a DERIVATION from that
  property, not a documented number. On PostgreSQL (recommended for production), raise MAX_WORKERS
  to the documented starting point of 2-8 workers per CPU core, e.g. os.cpu_count() * 4, then tune.

Also from Senzing's loading guidance: shuffle input rather than loading it sorted, and drain the
redo queue after loading (run redo as a separate process in production).
"""

import concurrent.futures
import itertools
import json
import os
import sys
import time
from pathlib import Path

from senzing import SzBadInputError, SzError, SzRetryableError, SzUnrecoverableError
from senzing_core import SzAbstractFactoryCore

MAX_WORKERS = 1  # SQLite: single writer (see CONCURRENCY above). PostgreSQL: os.cpu_count() * 4, then tune.
PROGRESS_EVERY = 1000
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
LOG_DIR = Path("logs")


def add_record(engine, record_to_add):
    record_dict = json.loads(record_to_add)
    data_source = record_dict.get("DATA_SOURCE", "")
    record_id = record_dict.get("RECORD_ID", "")
    engine.add_record(data_source, record_id, record_to_add)


class Loader:
    def __init__(self, data_source, input_file, instance_name):
        self.data_source = data_source
        self.input_file = Path(input_file)
        self.instance_name = instance_name
        LOG_DIR.mkdir(exist_ok=True)
        self.error_log = LOG_DIR / "loading_errors.json"
        self.checkpoint = LOG_DIR / f"load_checkpoint_{data_source}.json"
        self.loaded = self.failed = 0
        self.errors = []

    def _log_error(self, level, line_no, record, err):
        try:
            rid = json.loads(record).get("RECORD_ID")
        except (json.JSONDecodeError, AttributeError):
            rid = None
        entry = {"data_source": self.data_source, "line": line_no, "record_id": rid, "level": level,
                 "error": err.__class__.__name__, "message": str(err)}
        self.errors.append(entry)
        print(f"\n{level}: line {line_no} record {rid}: {err.__class__.__name__} - {err}", file=sys.stderr)

    def _resume_from(self, resume):
        if resume and self.checkpoint.exists():
            return json.loads(self.checkpoint.read_text()).get("completed_through_line", 0)
        return 0

    def _save_checkpoint(self, completed_through):
        self.checkpoint.write_text(json.dumps({"input_file": str(self.input_file),
                                               "completed_through_line": completed_through}))

    def run(self, resume=False, limit=None):
        skip = self._resume_from(resume)
        start = time.monotonic()
        sz_factory = SzAbstractFactoryCore(self.instance_name, SETTINGS, verbose_logging=False)
        engine = sz_factory.create_engine()
        in_flight = {}  # future -> (line_no, record)
        done_lines = set()
        low_water = skip  # every line <= low_water has completed
        with open(self.input_file, "r", encoding="utf-8") as in_file:
            numbered = ((n, line) for n, line in enumerate(in_file, 1) if n > skip and line.strip())
            if limit is not None:
                numbered = itertools.islice(numbered, limit)
            with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
                for n, rec in itertools.islice(numbered, MAX_WORKERS * 4):
                    in_flight[executor.submit(add_record, engine, rec)] = (n, rec)
                while in_flight:
                    done, _ = concurrent.futures.wait(in_flight, return_when=concurrent.futures.FIRST_COMPLETED)
                    for f in done:
                        n, rec = in_flight.pop(f)
                        try:
                            f.result()
                        except (SzBadInputError, json.JSONDecodeError) as err:
                            self._log_error("ERROR", n, rec, err)
                            self.failed += 1
                        except SzRetryableError as err:
                            self._log_error("WARN", n, rec, err)
                            self.failed += 1
                        except (SzUnrecoverableError, SzError):
                            self._finish(start, low_water)
                            raise
                        else:
                            self.loaded += 1
                        done_lines.add(n)
                        while low_water + 1 in done_lines:
                            low_water += 1
                            done_lines.discard(low_water)
                        nxt = next(numbered, None)
                        if nxt:
                            in_flight[executor.submit(add_record, engine, nxt[1])] = nxt
                        processed = self.loaded + self.failed
                        if processed % PROGRESS_EVERY == 0:
                            el = time.monotonic() - start
                            print(f"  {self.data_source}: {self.loaded:,} loaded, {self.failed:,} errors, "
                                  f"{el:,.1f}s, {processed / el:,.0f} records/s", flush=True)
                            self._save_checkpoint(low_water)
        return self._finish(start, low_water)

    def _finish(self, start, low_water):
        self._save_checkpoint(low_water)
        if self.errors:
            existing = json.loads(self.error_log.read_text()) if self.error_log.exists() else []
            self.error_log.write_text(json.dumps(existing + self.errors, indent=2))
        el = time.monotonic() - start
        stats = {"data_source": self.data_source, "input_file": str(self.input_file),
                 "attempted": self.loaded + self.failed, "loaded": self.loaded, "failed": self.failed,
                 "duration_s": round(el, 1), "records_per_s": round((self.loaded + self.failed) / el, 1) if el else None,
                 "max_workers": MAX_WORKERS, "error_log": str(self.error_log) if self.errors else None}
        print(json.dumps(stats, indent=2))
        return stats


def main(data_source, instance_name):
    import argparse
    ap = argparse.ArgumentParser(description=f"Load {data_source} Senzing JSONL into the repository")
    ap.add_argument("input", help="Senzing JSONL file to load")
    ap.add_argument("--resume", action="store_true", help="continue after the last checkpointed line")
    ap.add_argument("--limit", type=int, default=None, help="load only the first N records (test load)")
    args = ap.parse_args()
    try:
        stats = Loader(data_source, args.input, instance_name).run(resume=args.resume, limit=args.limit)
    except SzError as err:
        print(f"\nCRITICAL: {err.__class__.__name__} - {err}", file=sys.stderr)
        sys.exit(1)
    sys.exit(0 if stats["failed"] == 0 else 2)
