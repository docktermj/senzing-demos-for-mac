#!/usr/bin/env python3
"""Senzing JSON Analyzer — unified structural validation and quality analysis.

Requires Python 3.10 or newer. (It uses `match` statements, so an older
interpreter stops with a SyntaxError before any code runs.)

Reads Senzing JSONL from stdin, outputs a markdown analysis report.

Usage:
    python mapper.py data.csv | python sz_json_analyzer.py
    python sz_json_analyzer.py < mapped.jsonl
    python sz_json_analyzer.py -o report.md < mapped.jsonl
    python sz_json_analyzer.py --rel-explanation "targets loaded separately" < mapped.jsonl
    python sz_json_analyzer.py --self-test

Exit codes: 0 = no structural errors, 1 = structural errors found

Proven across local ollama models (rounds 3,7,9,12,14,15). Adds gates G1 RECORD_ID uniqueness, G2
near-miss key-typo nudge, G3 scalar feature values, G4 NAME_ORG vs person-name,
G4b RECORD_TYPE<->name consistency (ORGANIZATION->NAME_ORG only, PERSON->no
NAME_ORG), G7 empty value, G8 person/org name-plausibility WARNING (two
independent detectors, warns only on confident disagreement with RECORD_TYPE —
catches the "all-PERSON dodge" G4b can't; round 15), JSON-array->JSONL coaching,
and payload relaxed to any-type. cleanco is an optional enrichment for G8.
A9 relationship-structure integrity (round 18): only-one-REL-type across the
output is an ERROR (waivable by --rel-explanation; an unmatched REL_POINTER is a
WARNING, never broken) and G9 empty-FEATURES shells (per-record WARNING, dataset
ERROR when >50% featureless).
G10 feature fragmentation: parts of ONE feature split across SEPARATE objects in
one record (e.g. {"NAME_FIRST"} and {"NAME_LAST"}) is a structural ERROR — the
mirror of the mixed-families check, and the costlier half. Genuine multi-valued
features are exempt (they share a content attribute, or carry distinct usage
types); ADDR_FULL + ADDR_COUNTRY in ONE object is VALID.
"""

import difflib
import itertools
import json
import os
import sys

# G2 near-miss: an UNKNOWN uppercase key placed INSIDE a FEATURES object is feature-intent;
# if it is a close lexical match to a real catalog attribute it is almost certainly a typo, and
# Senzing will silently treat it as payload (the intended feature is lost). We only compare the
# model's own key against the catalog keys (keys-vs-catalog: answer-key-free, no value semantics),
# and we only NUDGE, never reject: an unknown key is legitimately payload by Senzing's model, so we
# cannot assert it is a typo. Cutoff is conservative (favor missing a borderline typo over flagging
# a genuine custom attribute).
NEAR_MISS_CUTOFF = 0.85

# ── Embedded feature config (from Senzing default config) ─────────────
# {feature_type: {"freq": frequency_class, "attrs": {attr_code: req_level}}}
# req: "Yes"=required, "Any"=at least one needed, "Desired"=recommended, "No"=optional
# Attr insertion order matches ATTR_ID (preserved in Python 3.7+ dicts).
FEATURES = {
    "RECORD_TYPE": {"freq": "FVM", "attrs": {
        "RECORD_TYPE": "Yes",
    }},
    "NAME": {"freq": "NAME", "attrs": {
        "NAME_TYPE": "No", "NAME_FULL": "Any", "NAME_ORG": "Any",
        "NAME_LAST": "Any", "NAME_FIRST": "Any", "NAME_MIDDLE": "No",
        "NAME_PREFIX": "No", "NAME_SUFFIX": "No", "NAME_GEN": "No",
    }},
    "GENDER": {"freq": "FVM", "attrs": {"GENDER": "Yes"}},
    "DOB": {"freq": "FM", "attrs": {
        "DATE_OF_BIRTH": "Yes", "DOB_YEAR": "No", "DOB_MONTH": "No",
        "DOB_DAY": "No", "DOB_HASH": "No",
    }},
    "DOD": {"freq": "FM", "attrs": {
        "DATE_OF_DEATH": "Yes", "DOD_YEAR": "No", "DOD_MONTH": "No",
        "DOD_DAY": "No", "DOD_HASH": "No",
    }},
    "NATIONALITY": {"freq": "FVM", "attrs": {"NATIONALITY": "Yes"}},
    "CITIZENSHIP": {"freq": "FVM", "attrs": {"CITIZENSHIP": "Yes"}},
    "PLACE_OF_BIRTH": {"freq": "FVM", "attrs": {"PLACE_OF_BIRTH": "Yes"}},
    "REGISTRATION_DATE": {"freq": "FM", "attrs": {"REGISTRATION_DATE": "Yes"}},
    "REGISTRATION_COUNTRY": {"freq": "FVM", "attrs": {"REGISTRATION_COUNTRY": "Yes"}},
    "COUNTRY_OF_ASSOCIATION": {"freq": "FVM", "attrs": {"COUNTRY_OF_ASSOCIATION": "Yes"}},
    "ADDRESS": {"freq": "FF", "attrs": {
        "ADDR_TYPE": "No", "ADDR_FULL": "Any", "ADDR_LINE1": "Any",
        "ADDR_LINE2": "No", "ADDR_LINE3": "No", "ADDR_LINE4": "No",
        "ADDR_LINE5": "No", "ADDR_LINE6": "No", "ADDR_CITY": "Any",
        "ADDR_STATE": "No", "ADDR_POSTAL_CODE": "Any", "ADDR_COUNTRY": "Any",
        "ADDR_STR_NUM": "No", "ADDR_STR_NAME": "No", "ADDR_UNIT_TYPE": "No",
        "ADDR_UNIT_NUM": "No", "ADDR_STR_NAME_STD": "No", "ADDR_CITY_STD": "No",
        "ADDR_POSTAL_5": "No", "ADDR_FROM_DATE": "No", "ADDR_THRU_DATE": "No",
    }},
    "PHONE": {"freq": "FF", "attrs": {
        "PHONE_TYPE": "No", "PHONE_NUMBER": "Yes", "PHONE_AREACODE": "No",
        "PHONE_EXT": "No", "PHONE_LAST_10": "No", "PHONE_LAST_5": "No",
        "PHONE_FROM_DATE": "No", "PHONE_THRU_DATE": "No",
    }},
    "EMAIL": {"freq": "F1", "attrs": {
        "EMAIL_ADDRESS": "Yes", "EMAIL_TYPE": "No",
        "EMAIL_FROM_DATE": "No", "EMAIL_THRU_DATE": "No",
    }},
    "WEBSITE": {"freq": "FF", "attrs": {"WEBSITE_ADDRESS": "Yes"}},
    "LOGIN_ID": {"freq": "F1", "attrs": {
        "SOCIAL_NETWORK": "Desired", "SOCIAL_HANDLE": "Yes",
        "SOCIAL_FROM_DATE": "No", "SOCIAL_THRU_DATE": "No",
    }},
    "ACCT_NUM": {"freq": "F1", "attrs": {
        "ACCOUNT_NUMBER": "Yes", "ACCOUNT_DOMAIN": "Desired",
    }},
    "SSN": {"freq": "F1", "attrs": {"SSN_NUMBER": "Yes", "SSN_NUMSTD": "No"}},
    "PASSPORT": {"freq": "F1", "attrs": {
        "PASSPORT_NUMBER": "Yes", "PASSPORT_COUNTRY": "Desired",
        "PASSPORT_ISSUE_DT": "No", "PASSPORT_EXPIRE_DT": "No", "PASSPORT_NUMSTD": "No",
    }},
    "DRLIC": {"freq": "F1", "attrs": {
        "DRIVERS_LICENSE_NUMBER": "Yes", "DRIVERS_LICENSE_STATE": "Desired",
        "DRIVERS_LICENSE_ISSUE_DT": "No", "DRIVERS_LICENSE_EXPIRE_DT": "No", "DRLIC_NUMSTD": "No",
    }},
    "NATIONAL_ID": {"freq": "F1", "attrs": {
        "NATIONAL_ID_TYPE": "Desired", "NATIONAL_ID_NUMBER": "Yes", "NATIONAL_ID_COUNTRY": "Desired",
        "NATIONAL_ID_ISSUE_DT": "No", "NATIONAL_ID_EXPIRE_DT": "No", "NATIONAL_ID_NUM_STD": "No",
    }},
    "TAX_ID": {"freq": "F1", "attrs": {
        "TAX_ID_NUMBER": "Yes", "TAX_ID_COUNTRY": "Desired",
        "TAX_ID_ISSUE_DT": "No", "TAX_ID_EXPIRE_DT": "No", "TAX_ID_NUM_STD": "No",
    }},
    "OTHER_ID": {"freq": "F1", "attrs": {
        "OTHER_ID_TYPE": "Desired", "OTHER_ID_NUMBER": "Yes", "OTHER_ID_COUNTRY": "Desired",
        "OTHER_ID_ISSUE_DT": "No", "OTHER_ID_EXPIRE_DT": "No", "OTHER_ID_NUM_STD": "No",
    }},
    "TRUSTED_ID": {"freq": "A1", "attrs": {
        "TRUSTED_ID_TYPE": "No", "TRUSTED_ID_NUMBER": "Yes",
    }},
    "DUNS_NUMBER": {"freq": "F1", "attrs": {"DUNS_NUMBER": "Yes"}},
    "NPI_NUMBER": {"freq": "F1", "attrs": {"NPI_NUMBER": "Yes"}},
    "LEI_NUMBER": {"freq": "F1", "attrs": {"LEI_NUMBER": "Yes"}},
    "GEO_LOC": {"freq": "FF", "attrs": {
        "GEO_LATLONG": "Any", "GEO_LATITUDE": "Any", "GEO_LONGITUDE": "Any",
        "GEO_PRECISION": "No",
    }},
    "GROUP_ASSOCIATION": {"freq": "FF", "attrs": {
        "GROUP_ASSOCIATION_TYPE": "No", "GROUP_ASSOCIATION_ORG_NAME": "Yes",
    }},
    "GROUP_ASSN_ID": {"freq": "FF", "attrs": {
        "GROUP_ASSN_ID_TYPE": "Yes", "GROUP_ASSN_ID_NUMBER": "Yes",
    }},
    "EMPLOYER": {"freq": "FF", "attrs": {
        "EMPLOYER_TYPE": "No", "EMPLOYER": "Yes",
    }},
    "VEH_LIC_PLATE": {"freq": "F1", "attrs": {
        "VEHICLE_LICENSE_PLATE_NUMBER": "Yes", "VEHICLE_LICENSE_PLATE_STATE": "Desired",
    }},
    "VEH_VIN": {"freq": "F1", "attrs": {"VEHICLE_VIN_NUMBER": "Yes"}},
    "MOBILE_AD_ID": {"freq": "FF", "attrs": {"MOBILE_AD_ID": "Yes"}},
    "REL_LINK": {"freq": "NONE", "attrs": {
        "RELATIONSHIP_TYPE": "No", "RELATIONSHIP_ROLE": "No", "RELATIONSHIP_KEY": "Yes",
        "RELATED_FROM_DATE": "No", "RELATED_THRU_DATE": "No",
    }},
    "REL_ANCHOR": {"freq": "NONE", "attrs": {
        "REL_ANCHOR_DOMAIN": "No", "REL_ANCHOR_KEY": "Yes",
    }},
    "REL_POINTER": {"freq": "NONE", "attrs": {
        "REL_POINTER_DOMAIN": "No", "REL_POINTER_KEY": "Yes", "REL_POINTER_ROLE": "No",
    }},
    "LINKEDIN": {"freq": "F1", "attrs": {"LINKEDIN": "Yes"}},
    "FACEBOOK": {"freq": "F1", "attrs": {"FACEBOOK": "Yes"}},
    "TWITTER": {"freq": "F1", "attrs": {"TWITTER": "Yes"}},
    "SKYPE": {"freq": "F1", "attrs": {"SKYPE": "Yes"}},
    "ZOOMROOM": {"freq": "F1", "attrs": {"ZOOMROOM": "Yes"}},
    "INSTAGRAM": {"freq": "F1", "attrs": {"INSTAGRAM": "Yes"}},
    "WHATSAPP": {"freq": "F1", "attrs": {"WHATSAPP": "Yes"}},
    "SIGNAL": {"freq": "F1", "attrs": {"SIGNAL": "Yes"}},
    "TELEGRAM": {"freq": "F1", "attrs": {"TELEGRAM": "Yes"}},
    "TANGO": {"freq": "F1", "attrs": {"TANGO": "Yes"}},
    "VIBER": {"freq": "F1", "attrs": {"VIBER": "Yes"}},
    "WECHAT": {"freq": "F1", "attrs": {"WECHAT": "Yes"}},
    "PLACEKEY": {"freq": "FF", "attrs": {"PLACEKEY": "No"}},
    "CREDITCARD": {"freq": "F1", "attrs": {"CREDITCARD_NUMBER": "Yes"}},
    "SEMANTIC_VALUE": {"freq": "FF", "attrs": {
        "SEMANTIC_EMBEDDING": "Yes", "SEMANTIC_ALGORITHM": "No", "SEMANTIC_LABEL": "Yes",
    }},
}

# Attrs to skip when computing feature composite value (usage types, date ranges)
SKIP_IN_VALUE = {
    "NAME_TYPE", "ADDR_TYPE", "PHONE_TYPE", "EMAIL_TYPE",
    "RELATIONSHIP_ROLE", "REL_POINTER_ROLE",
    "ADDR_FROM_DATE", "ADDR_THRU_DATE", "PHONE_FROM_DATE", "PHONE_THRU_DATE",
    "EMAIL_FROM_DATE", "EMAIL_THRU_DATE", "SOCIAL_FROM_DATE", "SOCIAL_THRU_DATE",
    "RELATED_FROM_DATE", "RELATED_THRU_DATE",
}

# G10 fragmentation: the usage-type attributes — the ONLY thing that legitimately
# distinguishes two objects of the same feature family that share no content attribute
# (a HOME address written as ADDR_FULL and a MAILING address written as parsed parts are
# two real addresses; the same two with no usage type are one address torn in half).
# Derived from SKIP_IN_VALUE so the two sets can never drift: NAME_TYPE, ADDR_TYPE,
# PHONE_TYPE, EMAIL_TYPE. The other *_TYPE attrs (NATIONAL_ID_TYPE, TAX_ID_TYPE, ...)
# are feature CONTENT, not usage types, and are deliberately absent from SKIP_IN_VALUE.
USAGE_TYPE_ATTRS = {a for a in SKIP_IN_VALUE if a.endswith("_TYPE")}

# The COMMON RECORD_TYPE values — NOT an allowlist. The Entity Specification is
# explicit that the set is open: "Many watchlists have standardized on values such as
# VESSEL and AIRCRAFT. You do not need to register these in Senzing to use them as
# RECORD_TYPE." So an unrecognized value is a WARNING (check it is an intrinsic type,
# not a role label), never an error. PERSON and ORGANIZATION are by far the most
# common; the rest are established conventions.
COMMON_RECORD_TYPES = {
    "PERSON",
    "ORGANIZATION",
    "VESSEL",
    "AIRCRAFT",
    "TRUST",
    "VEHICLE",
    "CLAIM",
    "PROVIDER",
}
ALLOWED_ROOT_KEYS = {"DATA_SOURCE", "RECORD_ID", "FEATURES"}
SCALAR_TYPES = (str, int, float, bool, type(None))
MAX_UNIQUE = 50_000


# ── Lookups ──────────────────────────────────────────────────────────
def build_attr_lookup(features):
    lookup = {}
    for ftype, fdata in features.items():
        for attr in fdata["attrs"]:
            lookup[attr] = ftype
    return lookup


def resolve_prefixed_attr(key, attr_lookup):
    """Resolve `<USAGE_TYPE>_<REGISTERED_ATTR>` to (attr, ftype, usage_type).

    In the flat shape the usage type is PREFIXED onto the attribute name instead of
    being carried as `NAME_TYPE`/`ADDR_TYPE` inside a feature object — so
    `BUSINESS_NAME_ORG` is `NAME_ORG` with usage type `BUSINESS`, and
    `BUSINESS_ADDR_LINE1` is `ADDR_LINE1` with usage type `BUSINESS`. The Entity
    Specification endorses the convention in reverse: "When a source uses clear
    prefixes (e.g., HOME_PHONE), you may derive `PHONE_TYPE` from the prefix."

    Without this, prefixed attributes were classified as PAYLOAD and the report
    claimed "No NAME features found" for records that plainly carry organization
    names — the raw CORD Las Vegas files (`PRIMARY_NAME_ORG`, `LEGAL_NAME_ORG`,
    `BUSINESS_ADDR_LINE1`) are exactly this shape.

    Conservative on purpose: strips the FEWEST leading tokens that yield a
    registered attribute, so the longest real attribute wins and a payload field
    that merely ends in a feature-ish word is far less likely to be claimed. Returns
    None when nothing resolves.
    """
    if key in attr_lookup:  # not prefixed; caller handles the direct hit
        return None
    parts = key.split("_")
    # Need at least one prefix token AND a remainder of at least one token.
    for cut in range(1, len(parts)):
        remainder = "_".join(parts[cut:])
        ftype = attr_lookup.get(remainder)
        if ftype:
            return remainder, ftype, "_".join(parts[:cut])
    return None


# ── Config loading ───────────────────────────────────────────────────
def config_from_export(config_data):
    """Convert Senzing config export JSON to FEATURES dict format."""
    freq_lookup = {}
    for ft in config_data.get("G2_CONFIG", {}).get("CFG_FTYPE", []):
        freq_lookup[ft["FTYPE_CODE"]] = ft["FTYPE_FREQ"]
    features = {}
    for attr in sorted(config_data.get("G2_CONFIG", {}).get("CFG_ATTR", []), key=lambda a: a["ATTR_ID"]):
        ftype = attr.get("FTYPE_CODE")
        if not ftype:
            continue
        if ftype not in features:
            features[ftype] = {"freq": freq_lookup.get(ftype, "NONE"), "attrs": {}}
        features[ftype]["attrs"][attr["ATTR_CODE"]] = attr.get("FELEM_REQ", "No")
    return features


def load_config():
    """Load config: --config file -> live SDK -> embedded default."""
    if "--config" in sys.argv:
        idx = sys.argv.index("--config")
        if idx + 1 < len(sys.argv):
            with open(sys.argv[idx + 1]) as f:
                return config_from_export(json.load(f)), "custom config"
    try:
        engine_config = os.environ.get("SENZING_ENGINE_CONFIGURATION_JSON")
        if engine_config:
            from senzing_core import SzAbstractFactoryCore
            factory = SzAbstractFactoryCore("sz_json_analyzer", engine_config)
            mgr = factory.create_configmanager()
            cfg = mgr.create_config_from_config_id(mgr.get_default_config_id())
            return config_from_export(json.loads(cfg.export())), "live Senzing config"
    except Exception:
        pass
    return FEATURES, "default config"


# ── Stats helpers ────────────────────────────────────────────────────
def _new_stats():
    return {"count": 0, "values": {}}


def _add_value(stats, value):
    stats["count"] += 1
    if value in stats["values"]:
        stats["values"][value] += 1
    elif len(stats["values"]) < MAX_UNIQUE:
        stats["values"][value] = 1


# ── G8: person/org name-plausibility (WARNING, output-only, answer-key-free) ──
# TWO INDEPENDENT detectors over the EMITTED record (name + feature keys); warn ONLY on a CONFIDENT
# DISAGREEMENT with the declared RECORD_TYPE (looks-org∧not-person on a PERSON record, or
# looks-person∧not-org on an ORGANIZATION record); ABSTAIN otherwise. No source, no mapping oracle —
# only general name shape + the Senzing feature catalog. A WARNING, NEVER a hard error: a
# probabilistic signal must not gate (it would be a footgun on other data), but surfaced in the
# report the LLM runs at step 4 it nudges a re-type — which is what catches the "all-PERSON dodge"
# (typing every record PERSON to dodge NAME_ORG, internally consistent so G4b can't see it).
# Dependency-free heuristics below; a richer multi-cultural form (name-dataset + cleanco) can replace
# the token tests without changing the two-detector agreement logic.
_PERSON_FEATS = {"DATE_OF_BIRTH", "GENDER", "PASSPORT_NUMBER", "PLACE_OF_BIRTH", "SSN_NUMBER"}
_ORG_SIGNAL_FEATS = {"LEI_NUMBER", "REGISTRATION_DATE", "DUNS_NUMBER", "WEBSITE_ADDRESS"}
_ORG_FUNC_WORDS = {"OF", "FOR", "THE", "AND", "&", "UL", "DEL", "DE", "LA", "LE"}
_ORG_TERMS = {
    "LLC", "INC", "INCORPORATED", "LTD", "LIMITED", "CORP", "CORPORATION", "GMBH", "SA", "PLC", "CO", "COMPANY", "SOCIETY", "STICHTING",
    "FOUNDATION", "COMMITTEE", "ASSOCIATION", "ORGANIZATION", "ORGANISATION", "BANK", "GROUP",
    "TRADING", "BROS", "VZW", "INDUSTRIES", "HOLDINGS", "ENTERPRISES", "INSTITUTE", "AGENCY",
}
# Optional multi-cultural legal-term detection (~50 jurisdictions). If absent the structural
# heuristics above still run; measured recall on the OFAC mixed set was 60% without / 68% with.
try:
    from cleanco import basename as _cleanco_basename
except Exception:  # pragma: no cover - cleanco is an optional enrichment
    _cleanco_basename = None


def _personish_token(tok):
    """A bare heuristic for 'looks like a human-name token': a Capitalized word with lowercase
    letters (Behrouz), vs an ALL-CAPS org token (SOCIETY). The multi-cultural upgrade replaces this
    with name-dataset membership (casing-independent); the agreement logic is unchanged."""
    return len(tok) > 1 and tok[0].isupper() and any(c.islower() for c in tok) and tok.isalpha()


def person_org_signals(name, feature_keys):
    """Two INDEPENDENT verdicts from the emitted record only: (looks_person, looks_org)."""
    toks = name.split()
    norm = {t.strip(".,").upper() for t in toks}
    pcount = sum(_personish_token(t) for t in toks)
    looks_person = bool(_PERSON_FEATS & feature_keys) or (
        bool(toks) and pcount >= 1 and len(toks) <= 3 and not (norm & _ORG_FUNC_WORDS)
    )
    looks_org = (
        bool(_ORG_SIGNAL_FEATS & feature_keys)
        or bool(norm & _ORG_TERMS)
        or bool(norm & _ORG_FUNC_WORDS)
        or (len(toks) >= 4 and (pcount / len(toks) if toks else 0) < 0.5)
        or any(c.isdigit() for c in name)
    )
    if not looks_org and _cleanco_basename is not None:
        try:  # multi-cultural legal term present? (e.g. "… S.A", "… GmbH")
            looks_org = _cleanco_basename(name).strip().upper() != name.strip().upper()
        except Exception:  # pragma: no cover
            pass
    return looks_person, looks_org


# ── Analyzer ─────────────────────────────────────────────────────────
def _fragmentation_hint(family):
    """Family-specific remediation for a G10 fragmentation error."""
    match family:
        case "NAME":
            return (
                "Put every name part in ONE object: "
                '{"NAME_FIRST": "John", "NAME_LAST": "Smith"}.'
            )
        case "ADDRESS":
            return (
                "Put every address part in ONE object: "
                '{"ADDR_LINE1": "123 Main St", "ADDR_CITY": "Springfield", '
                '"ADDR_STATE": "IL", "ADDR_POSTAL_CODE": "62701"} — and keep '
                "ADDR_COUNTRY with ADDR_FULL rather than splitting it out."
            )
        case "PHONE":
            return (
                "Put the number and its qualifiers in ONE object: "
                '{"PHONE_NUMBER": "702-919-1300", "PHONE_TYPE": "MOBILE"}.'
            )
        case _:
            return f"Merge them into a single {family} object."


def _qualifier_hint(family, quals):
    """Fix for a qualifier that sits apart from its feature's value."""
    names = ", ".join(quals)
    return (
        f"Add {names} to the existing {family} object (constants are extra keys inside "
        f"the feature object they qualify), or drop it if this record has no {family}."
    )


class Analyzer:

    def __init__(self, features):
        self.features = features
        self.attr_lookup = build_attr_lookup(features)
        self.record_count = 0
        self.errors = []
        self.data_sources = {}
        self.feature_stats = {}
        self.payload_stats = {}
        self.custom_attrs = {}
        # G2: unknown feature-position key -> (suggested catalog attr, hit count)
        self.near_miss = {}
        self.has_record_type = 0
        self.missing_record_id = 0
        # G8: (line, declared_type, looks_like, name) where the name confidently disagrees with type
        self.type_name_mismatches = []
        # RECORD_ID must be a UNIQUE primary key per (DATA_SOURCE, RECORD_ID).
        self.seen_record_ids = {}  # (data_source, record_id) -> first line seen
        self.dup_record_ids = set()  # keys already reported as duplicate (report once)
        # G9: records whose FEATURES array is EMPTY (structurally valid but unmatchable).
        self.empty_features_records = []
        # Qualifier-only features: (line, family, [qualifiers]) — a usage type with no
        # value of its feature in the record. A WARNING, not an error: Senzing ignores
        # it, and the canonical truth set itself carries 4 ({"PHONE_TYPE":"MOBILE"} where
        # the number was blank). As a MAPPING constant it is an error at step 3 instead.
        self.qualifier_only = []
        # RECORD_TYPE value -> count, across the whole dataset. The SET matters: records
        # with different RECORD_TYPEs CANNOT resolve together, so a dataset carrying more
        # than one type is making a deliberate (and easily accidental) partitioning
        # decision that deserves to be surfaced loudly.
        self.record_types = {}
        # Values outside COMMON_RECORD_TYPES -> first line seen (warning, not error).
        self.uncommon_record_types = {}
        # Flat-shape observations (NOT errors — the spec still supports the flat form).
        # Records with no FEATURES array at all:
        self.flat_shape_records = []
        # Registered feature attributes found at the record root -> first line seen:
        self.root_feature_attrs = {}
        # Feature TYPES (NAME, ADDRESS, ...) present at the root via the flat shape.
        # Kept separately from feature_stats, which only counts inside FEATURES.
        self.root_feature_types = set()
        # `<USAGE_TYPE>_<ATTR>` root keys -> (attr, ftype, usage_type).
        self.prefixed_attrs = {}
        # Cross-record relationship integrity: a REL_POINTER needs a REL_ANCHOR to point AT.
        self.all_record_ids = set()  # every RECORD_ID emitted
        self.rel_anchor_keys = set()  # every REL_ANCHOR_KEY emitted
        self.rel_pointers = []  # (line_num, REL_POINTER_KEY)
        self.rel_structure_note = ""  # only-one-type accepted-with-explanation note (finalize)

    def _error(self, line_num, msg):
        if len(self.errors) < 200:
            self.errors.append((line_num, msg))

    def finalize(self, explanation=""):
        """Dataset-level checks that need the whole output (run after all records).

        Relationships need BOTH a REL_POINTER (the link) and a REL_ANCHOR (the addressable target).
        Having only ONE type across the whole output — pointers with no anchor anywhere, OR anchors
        with no pointer anywhere — is a structural error at the SAME level: the relationship structure
        is incomplete (nothing to point at, or nothing pointing). It is ACCOUNTABLE, not absolute:
        a non-empty `explanation` waives it (e.g. "this source has no relationships; anchors enable
        inbound matching", or "all targets are external, loaded separately") — recorded, not silently
        passed. A per-pointer non-match is NOT conclusive in a small sample (target may be external),
        so it stays a warning, handled in the report.
        """
        # G9 (dataset-level): MOST records featureless = the hollow-mapper signature
        # (a broken mapper emitting shells — e.g. nested access against a flat source).
        # >50% threshold tolerates a legitimate payload-only minority; per-record empties
        # below the threshold stay warnings in the report.
        n_empty = len(self.empty_features_records)
        if self.record_count and n_empty / self.record_count > 0.5:
            self._error(
                0,
                f"G9: {n_empty} of {self.record_count} records have an EMPTY FEATURES array — "
                "the mapper is emitting shells with nothing to match on. Verify the mapper reads "
                "the REAL source records (check actual field names/shapes in the source file, "
                "not an assumed structure) and inspect a line of the produced output.",
            )

        has_anchor = bool(self.rel_anchor_keys)
        has_pointer = bool(self.rel_pointers)
        if has_anchor == has_pointer:  # both present or both absent -> fine
            return
        which = "REL_POINTER(s) but ZERO REL_ANCHOR" if has_pointer else "REL_ANCHOR(s) but ZERO REL_POINTER"
        detail = (
            f"Output has {which} anywhere — a relationship needs BOTH the link (REL_POINTER) and an "
            "addressable target (REL_ANCHOR). Only one type present means the relationship structure "
            "is incomplete: anchor every participating record (REL_ANCHOR_KEY = RECORD_ID) and emit a "
            "REL_POINTER for each link."
        )
        if explanation.strip():
            self.rel_structure_note = f"{detail} ACCEPTED with explanation: {explanation.strip()}"
        else:
            self._error(0, detail + " If intentional, re-run with an explanation.")

    def _check_fragmentation(self, features_list, line_num):
        """G10: parts of ONE feature instance split across SEPARATE FEATURES objects.

        The mirror image of the "Mixed families in one object" check below, and the more
        damaging of the two. ``{"NAME_FIRST":"John"}`` plus ``{"NAME_LAST":"Smith"}`` is
        not one name — Senzing builds TWO NAME features, "John" and "Smith", and neither
        matches "John Smith". Measured cost: 119 of 159 truth-set records mapped this way
        resolved to 86 entities against a published answer key of 85, with 18 clusters
        wrong (plugin E2E run, 2026-09-21).

        Two objects of the same family are FRAGMENTS when their CONTENT attributes (the
        family's attributes minus the SKIP_IN_VALUE qualifiers) are disjoint. Genuine
        multi-valued features never look like this: two addresses both carry ADDR_LINE1 or
        ADDR_FULL, two phones both carry PHONE_NUMBER, two names both carry a name part.
        The one legitimate disjoint shape — the same feature recorded two different ways
        for two different uses — is identified by DIFFERENT explicit usage types, and is
        exempted. Verified against the canonical truth set: zero fires across all 159
        records and every family.
        """
        groups = {}
        for idx, feat in enumerate(features_list):
            if not isinstance(feat, dict):
                continue
            per_family = {}
            for k in feat:
                fam = self.attr_lookup.get(k)
                if fam:
                    per_family.setdefault(fam, set()).add(k)
            for fam, attrs in per_family.items():
                content = frozenset(a for a in attrs if a not in SKIP_IN_VALUE)
                usage = frozenset(
                    (a, str(feat[a])) for a in attrs if a in USAGE_TYPE_ATTRS
                )
                groups.setdefault(fam, []).append((idx, content, usage, frozenset(attrs)))

        for fam, objs in sorted(groups.items()):
            # A qualifier with no value of its feature anywhere in the record (a lone
            # {"ADDR_TYPE": "BUSINESS"} and no address) is not a feature: it qualifies
            # nothing. Seen when a mapping constant was emitted as its own object.
            if not any(content for _, content, _, _ in objs):
                quals = sorted({a for _, _, _, attrs in objs for a in attrs})
                self.qualifier_only.append((line_num, fam, quals))
                continue
            if len(objs) < 2:
                continue
            for (i1, c1, u1, a1), (i2, c2, u2, a2) in itertools.combinations(objs, 2):
                if c1 & c2:
                    continue  # share a content attribute -> two real instances
                if not (c1 or c2):
                    continue  # qualifier-only on both sides -> nothing to join
                if u1 and u2 and u1 != u2:
                    continue  # explicitly different usage types -> two real instances
                self._error(
                    line_num,
                    f"FEATURES[{i1}] and FEATURES[{i2}]: FRAGMENTED {fam} feature — "
                    f"{sorted(c1) or '(qualifiers only)'} and "
                    f"{sorted(c2) or '(qualifiers only)'} are parts of ONE {fam} but sit "
                    f"in SEPARATE objects, so Senzing builds two partial {fam} features "
                    f"instead of one. "
                    f"{_qualifier_hint(fam, sorted(a1 if not c1 else a2)) if not (c1 and c2) else _fragmentation_hint(fam)}"
                    f" If these really are two "
                    f"DIFFERENT {fam} values, give each object its own distinct usage type "
                    f"so they are distinguishable.",
                )

    def analyze(self, record, line_num):
        self.record_count += 1

        if not isinstance(record, dict):
            if isinstance(record, list):
                self._error(
                    line_num,
                    "You emitted a JSON array; Senzing needs JSONL — one compact JSON object PER "
                    "LINE (no '[' ']' brackets, no pretty-printing).",
                )
            else:
                self._error(line_num, "Root must be a JSON object")
            return

        # ── Root validation ──
        ds = record.get("DATA_SOURCE")
        if not ds or not isinstance(ds, str):
            self._error(line_num, "Missing or non-string DATA_SOURCE")
        else:
            self.data_sources[ds] = self.data_sources.get(ds, 0) + 1

        # RECORD_ID must exist and be a UNIQUE primary key. If no source field is unique,
        # the mapper must DERIVE one (e.g. a hash of the record) — that guidance is echoed in
        # the failure message so a model that picks a non-unique field (e.g. Name) learns the fix.
        rid = record.get("RECORD_ID")
        ds_key = ds if isinstance(ds, str) else ""
        if "RECORD_ID" not in record:
            self.missing_record_id += 1
            self._error(
                line_num,
                "Missing RECORD_ID — every record needs a RECORD_ID that is UNIQUE per record "
                "(the primary key). If no source field is unique, derive one (e.g. a hash of the record).",
            )
        elif not isinstance(rid, str):
            self._error(line_num, "RECORD_ID must be a string")
        else:
            key = (ds_key, rid)
            if key in self.seen_record_ids and key not in self.dup_record_ids:
                self.dup_record_ids.add(key)
                self._error(
                    line_num,
                    f"Duplicate RECORD_ID {rid!r} in DATA_SOURCE {ds_key!r} (first seen at line "
                    f"{self.seen_record_ids[key]}) — RECORD_ID must be UNIQUE per record (the primary "
                    "key). A non-unique field like a name is NOT a valid key; if no source field is "
                    "unique, derive one (e.g. a hash of the record's fields).",
                )
            else:
                self.seen_record_ids.setdefault(key, line_num)
        if isinstance(rid, str):
            self.all_record_ids.add(rid)

        features_list = record.get("FEATURES")
        if not isinstance(features_list, list):
            # NOT an error. The FEATURES array is the RECOMMENDED schema, not a required
            # one. The Entity Specification: "In prior versions we allowed a flat JSON
            # structure with a separate sub-list for each feature that had multiple
            # values. WHILE WE STILL SUPPORT THAT, we now recommend the following JSON
            # schema that has just one list for all features."
            #
            # So a record with feature attributes at the root, or with per-feature named
            # sub-lists (NAMES, ADDRESSES, ...), is valid Senzing JSON that loads and
            # resolves. Erroring here told users their data was broken when it was not:
            # the raw CORD Las Vegas vendor files use both flat forms, and the engine
            # resolves them into cross-source entities on +NAME+ADDRESS.
            self.flat_shape_records.append(line_num)
            features_list = []
        elif not features_list:
            # G9: an EMPTY FEATURES array — the record is structurally valid but carries
            # nothing Senzing can match on. Per-record it is a WARNING (a payload-only
            # record is a legitimate corner); dataset-level it errors in finalize() when
            # MOST records are featureless (the hollow-mapper signature). Output-only,
            # answer-key-free (structure, never values-vs-meaning).
            self.empty_features_records.append(line_num)

        for k, v in record.items():
            if k in ALLOWED_ROOT_KEYS:
                continue
            if k in self.attr_lookup:
                # A registered feature attribute at the ROOT is the flat shape, which the
                # Entity Specification still supports (see the FEATURES note above). It is
                # a schema-preference observation, not an error: Senzing extracts the
                # feature either way. Recorded per-attribute so the report can name them.
                self.root_feature_attrs.setdefault(k, line_num)
                self.root_feature_types.add(self.attr_lookup[k])
                # RECORD_TYPE at the root counts exactly as much as one inside FEATURES:
                # it is the same feature and it partitions resolution the same way.
                # Counting only the FEATURES position made the report claim "RECORD_TYPE
                # not found" for flat records that declare it, and — worse — kept the
                # different-types-cannot-resolve warning from ever firing on flat data,
                # which is the shape most likely to have been mapped inconsistently.
                if k == "RECORD_TYPE" and isinstance(v, str):
                    self.has_record_type += 1
                    self.record_types[v] = self.record_types.get(v, 0) + 1
                    if v not in COMMON_RECORD_TYPES:
                        self.uncommon_record_types.setdefault(v, line_num)
                continue
            prefixed = resolve_prefixed_attr(k, self.attr_lookup)
            if prefixed:
                # `<USAGE_TYPE>_<ATTR>` at the root — a FEATURE, not payload. Recording
                # the resolved feature TYPE is what stops the report claiming "No NAME
                # features found" for a record whose name is in `PRIMARY_NAME_ORG`.
                attr, ftype, usage = prefixed
                self.root_feature_attrs.setdefault(k, line_num)
                self.root_feature_types.add(ftype)
                self.prefixed_attrs.setdefault(k, (attr, ftype, usage))
                continue
            # Any non-reserved, non-feature root key IS payload by Senzing's model: a field is
            # classified by its KEY (registered feature attribute -> feature, belongs in FEATURES;
            # everything else -> payload, carried but NOT matched on). There is no "declare your
            # fields" step and no illegal-unknown-field category. Payload may therefore be ANY type
            # — we do NOT scalar-check it. (Phase 1 also legitimately carries raw multi-value source
            # fields here for a later extraction pass, e.g. an Alias list of "a.k.a. | strong |
            # NAME" text.) The shipped spec/analyzer over-restrict payload to scalar — see
            # mcp_feedback round 7. Feature VALUES inside FEATURES must still be scalar (below).
            if v is not None and str(v).strip():
                if k not in self.payload_stats:
                    self.payload_stats[k] = _new_stats()
                _add_value(self.payload_stats[k], str(v))

        # ── FEATURES validation + stats ──
        seen_rel_anchor = False
        record_features_seen = set()

        for idx, feat in enumerate(features_list):
            loc = f"FEATURES[{idx}]"
            if not isinstance(feat, dict):
                self._error(line_num, f"{loc}: Must be an object")
                continue

            for kk, vv in feat.items():
                if not isinstance(vv, SCALAR_TYPES):
                    self._error(line_num, f"{loc}: '{kk}' must be scalar, found {type(vv).__name__}")
                elif isinstance(vv, str) and not vv.strip():
                    # G7: an empty/whitespace feature value is meaningless — Senzing errors on
                    # empty required elements (SENZ7313). Fix the value, or drop/omit the attribute.
                    self._error(
                        line_num, f"{loc}: '{kk}' is empty — feature values must be non-empty"
                    )

            # Detect families and unknowns
            families = []
            unknown = []
            for k in feat:
                fam = self.attr_lookup.get(k)
                if fam:
                    if fam not in families:
                        families.append(fam)
                elif k.isupper():
                    unknown.append(k)

            # RECORD_TYPE check
            if "RECORD_TYPE" in feat:
                self.has_record_type += 1
                rt = feat.get("RECORD_TYPE")
                if isinstance(rt, str):
                    # Every distinct value is tracked, because the SET of types present in
                    # a dataset is the thing that actually changes resolution behaviour:
                    # records with DIFFERENT RECORD_TYPEs cannot resolve together.
                    self.record_types.setdefault(rt, 0)
                    self.record_types[rt] += 1
                    if rt not in COMMON_RECORD_TYPES:
                        # WARNING, not an error — the value set is open by design.
                        self.uncommon_record_types.setdefault(rt, line_num)

            # Address: ADDR_FULL vs parsed parts.
            # ADDR_COUNTRY is deliberately NOT in this set. It is a qualifier of the whole
            # address, not a piece carved out of the street/city/state/postal line that
            # ADDR_FULL already contains, and Senzing's OWN canonical truth set pairs the
            # two: 4 of the 159 records in data/current/cord/truthset/*.jsonl pair them in
            # ONE object — customers.jsonl:91 (RECORD_ID 2011), customers.jsonl:98 (2142),
            # reference.jsonl:6 (2061) and reference.jsonl:19 (2141). Flagging it as a
            # conflict taught the mapper to SPLIT ADDR_COUNTRY into its own object, which is
            # the G10 fragmentation defect this analyzer now rejects — the analyzer was
            # producing the error it is supposed to catch.
            if "ADDR_FULL" in feat:
                parts = {"ADDR_LINE1", "ADDR_LINE2", "ADDR_LINE3", "ADDR_LINE4",
                         "ADDR_LINE5", "ADDR_LINE6", "ADDR_CITY", "ADDR_STATE",
                         "ADDR_POSTAL_CODE"}
                mixed = parts & set(feat)
                if mixed:
                    self._error(line_num, f"{loc}: ADDR_FULL cannot combine with {sorted(mixed)}")

            # Name: NAME_FULL vs parsed parts
            if "NAME_FULL" in feat:
                parts = {"NAME_ORG", "NAME_LAST", "NAME_FIRST", "NAME_MIDDLE",
                         "NAME_SUFFIX", "NAME_PREFIX"}
                mixed = parts & set(feat)
                if mixed:
                    self._error(line_num, f"{loc}: NAME_FULL cannot combine with {sorted(mixed)}")

            # REL_ANCHOR rules
            if "REL_ANCHOR" in families:
                if "REL_ANCHOR_DOMAIN" not in feat or "REL_ANCHOR_KEY" not in feat:
                    self._error(line_num, f"{loc}: REL_ANCHOR requires both DOMAIN and KEY")
                if seen_rel_anchor:
                    self._error(line_num, f"{loc}: Multiple REL_ANCHOR not allowed")
                seen_rel_anchor = True
                if feat.get("REL_ANCHOR_KEY"):
                    self.rel_anchor_keys.add(feat["REL_ANCHOR_KEY"])

            # REL_POINTER rules
            if "REL_POINTER" in families:
                if "REL_POINTER_DOMAIN" not in feat or "REL_POINTER_KEY" not in feat:
                    self._error(line_num, f"{loc}: REL_POINTER requires both DOMAIN and KEY")
                if feat.get("REL_POINTER_KEY"):
                    self.rel_pointers.append((line_num, feat["REL_POINTER_KEY"]))

            # Can't mix anchor and pointer
            has_anchor = "REL_ANCHOR_DOMAIN" in feat or "REL_ANCHOR_KEY" in feat
            has_pointer = "REL_POINTER_DOMAIN" in feat or "REL_POINTER_KEY" in feat
            if has_anchor and has_pointer:
                self._error(line_num, f"{loc}: Cannot mix REL_ANCHOR and REL_POINTER")

            # Track unknown attrs as custom features (warning, not error)
            for u in unknown:
                if u not in self.custom_attrs:
                    self.custom_attrs[u] = _new_stats()
                v = feat.get(u)
                if v is not None and str(v).strip():
                    _add_value(self.custom_attrs[u], str(v))
                # G2 near-miss: does this unknown feature-position key closely match a catalog
                # attribute (likely typo -> silent feature loss)? Compare key-vs-catalog only.
                if u not in self.near_miss:
                    match = difflib.get_close_matches(
                        u, list(self.attr_lookup), n=1, cutoff=NEAR_MISS_CUTOFF
                    )
                    if match:
                        self.near_miss[u] = [match[0], 0]
                if u in self.near_miss:
                    self.near_miss[u][1] += 1

            # Family validation
            if len(families) > 1:
                self._error(line_num, f"{loc}: Mixed families {families}; split into separate objects")
            elif len(families) == 1:
                fam = families[0]
                allowed = set(self.features.get(fam, {}).get("attrs", {}).keys())
                for kk in feat:
                    if kk not in allowed and kk not in unknown:
                        other_fam = self.attr_lookup.get(kk)
                        if other_fam and other_fam != fam:
                            self._error(line_num, f"{loc}: '{kk}' belongs to {other_fam}, not {fam}")

                # ── Collect feature stats ──
                if fam not in self.feature_stats:
                    self.feature_stats[fam] = {"count": 0, "values": {}, "attrs": {}}

                if fam not in record_features_seen:
                    self.feature_stats[fam]["count"] += 1
                    record_features_seen.add(fam)

                value_parts = []
                for k, v in feat.items():
                    if k in SKIP_IN_VALUE or not v:
                        continue
                    if self.attr_lookup.get(k) == fam:
                        value_parts.append(str(v))
                        if k not in self.feature_stats[fam]["attrs"]:
                            self.feature_stats[fam]["attrs"][k] = _new_stats()
                        _add_value(self.feature_stats[fam]["attrs"][k], str(v))

                if value_parts:
                    composite = " ".join(value_parts)
                    vs = self.feature_stats[fam]["values"]
                    if composite in vs:
                        vs[composite] += 1
                    elif len(vs) < MAX_UNIQUE:
                        vs[composite] = 1

        # G10 (cross-object, per record): parts of ONE feature torn across SEPARATE objects.
        self._check_fragmentation(features_list, line_num)

        # G4 (cross-feature): NAME_ORG cannot co-exist with ANY person-name attribute in one record.
        # A record is a person OR an org, not both — Senzing's step-3 validator enforces this; the
        # only person/org structural rule the spec actually supports (no broad feature partition,
        # since RECORD_TYPE is customer-defined). Message states the fix (don't just name the rule).
        PERSON_NAME_ATTRS = {
            "NAME_FULL", "NAME_LAST", "NAME_FIRST", "NAME_MIDDLE", "NAME_PREFIX", "NAME_SUFFIX"
        }
        has_org = any("NAME_ORG" in f for f in features_list if isinstance(f, dict))
        person_attrs = sorted(
            {a for f in features_list if isinstance(f, dict) for a in f if a in PERSON_NAME_ATTRS}
        )
        if has_org and person_attrs:
            self._error(
                line_num,
                f"NAME_ORG cannot co-exist with person name attribute(s) {person_attrs} in one "
                f"record — one record is a person OR an organization, never both. In the mapping, "
                f"put the organization-name column and the person-name columns under DIFFERENT "
                f"type_discriminator types so each record gets one kind. If the source itself "
                f"gives this record both kinds, ask the user which it is, then correct that "
                f"source row: a mapping cannot choose for them.",
            )

        # G4b (cross-feature, RECORD_TYPE-conditioned): the declared RECORD_TYPE dictates the
        # name attribute. ORGANIZATION carries NAME_ORG only (no person-name attrs); PERSON carries
        # person-name attrs only (no NAME_ORG). Catches the case plain G4 cannot: an org record that
        # used NAME_FULL with NO NAME_ORG (so has_org is false). Answer-key-free: keys off the
        # record's own RECORD_TYPE enum + the catalog name-attr classes, never the name's meaning.
        record_type = next(
            (
                f["RECORD_TYPE"]
                for f in features_list
                if isinstance(f, dict) and isinstance(f.get("RECORD_TYPE"), str)
            ),
            None,
        )
        if record_type == "ORGANIZATION" and person_attrs:
            self._error(
                line_num,
                f"RECORD_TYPE=ORGANIZATION must use NAME_ORG, not person name attribute(s) "
                f"{person_attrs} — map an organization's name(s) to NAME_ORG.",
            )
        elif record_type == "PERSON" and has_org:
            self._error(
                line_num,
                "RECORD_TYPE=PERSON must not carry NAME_ORG — use NAME_FULL (or parsed name parts) "
                "for a person; NAME_ORG is for organization records.",
            )

        # G8 (WARNING): does the NAME's own evidence confidently disagree with RECORD_TYPE?
        if record_type in ("PERSON", "ORGANIZATION"):
            name_val = next(
                (
                    f[k]
                    for f in features_list
                    if isinstance(f, dict)
                    for k in ("NAME_FULL", "NAME_ORG")
                    if isinstance(f.get(k), str) and f[k].strip()
                ),
                "",
            )
            if name_val:
                fkeys = {k for f in features_list if isinstance(f, dict) for k in f}
                looks_person, looks_org = person_org_signals(name_val, fkeys)
                if record_type == "PERSON" and looks_org and not looks_person:
                    self.type_name_mismatches.append((line_num, "PERSON", "ORGANIZATION", name_val))
                elif record_type == "ORGANIZATION" and looks_person and not looks_org:
                    self.type_name_mismatches.append((line_num, "ORGANIZATION", "PERSON", name_val))


# ── Report generation ────────────────────────────────────────────────
def _top(values, n=3):
    if not values:
        return ""
    top = sorted(values.items(), key=lambda x: -x[1])[:n]
    return ", ".join(f"{v[:50]} ({c})" for v, c in top)


def _pct(num, denom):
    return f"{num / denom * 100:.1f}%" if denom else "0%"


def generate_report(a, config_source):
    rc = a.record_count
    lines = ["# Senzing JSON Analysis Report", ""]
    lines.append(f"**Records:** {rc:,} | **Config:** {config_source}")
    if a.data_sources:
        ds = ", ".join(f"{k} ({v:,})" for k, v in sorted(a.data_sources.items()))
        lines.append(f"**Data Sources:** {ds}")
    lines.append("")

    # Structural errors
    if a.errors:
        capped = "+ (capped)" if len(a.errors) >= 200 else ""
        lines.append(f"## Structural Errors ({len(a.errors)}{capped})")
        lines.append("")
        for ln, msg in a.errors[:20]:
            lines.append(f"- **Line {ln}:** {msg}")
        if len(a.errors) > 20:
            lines.append(f"- ... and {len(a.errors) - 20} more")
        lines.append("")

    if rc == 0:
        lines.append("*No records to analyze.*")
        return "\n".join(lines)

    # Feature analysis table
    if a.feature_stats:
        lines.append("## Feature Analysis")
        lines.append("")
        lines.append("| Feature | Freq | Records | Pop% | Unique | Uniq% | Top Values |")
        lines.append("|---------|------|---------|------|--------|-------|------------|")

        feature_order = list(a.features.keys())
        for f in a.feature_stats:
            if f not in feature_order:
                feature_order.append(f)

        for ftype in feature_order:
            if ftype not in a.feature_stats:
                continue
            fs = a.feature_stats[ftype]
            freq = a.features.get(ftype, {}).get("freq", "?")
            ct, uniq = fs["count"], len(fs["values"])
            lines.append(
                f"| **{ftype}** | {freq} | {ct:,} | {_pct(ct, rc)} "
                f"| {uniq:,} | {_pct(uniq, ct)} | {_top(fs['values'])} |"
            )
            # Sub-attributes
            attrs = fs.get("attrs", {})
            show_attrs = len(attrs) > 1 or (len(attrs) == 1 and list(attrs)[0] != ftype)
            if show_attrs:
                config_order = list(a.features.get(ftype, {}).get("attrs", {}).keys())
                shown = set()
                for ak in config_order:
                    if ak in attrs:
                        shown.add(ak)
                        ast = attrs[ak]
                        lines.append(
                            f"| {ak} | | {ast['count']:,} | {_pct(ast['count'], rc)} "
                            f"| {len(ast['values']):,} | {_pct(len(ast['values']), ast['count'])} "
                            f"| {_top(ast['values'])} |"
                        )
                for ak, ast in attrs.items():
                    if ak not in shown:
                        lines.append(
                            f"| {ak} | | {ast['count']:,} | {_pct(ast['count'], rc)} "
                            f"| {len(ast['values']):,} | {_pct(len(ast['values']), ast['count'])} "
                            f"| {_top(ast['values'])} |"
                        )
        lines.append("")
    elif a.errors:
        lines.append("## Feature Analysis")
        lines.append("")
        lines.append("*Feature analysis skipped due to structural errors above.*")
        lines.append("")

    # Payload attributes
    if a.payload_stats:
        lines.append("## Payload Attributes")
        lines.append("")
        lines.append("| Attribute | Records | Pop% | Unique | Top Values |")
        lines.append("|-----------|---------|------|--------|------------|")
        for attr in sorted(a.payload_stats):
            ps = a.payload_stats[attr]
            lines.append(
                f"| {attr} | {ps['count']:,} | {_pct(ps['count'], rc)} "
                f"| {len(ps['values']):,} | {_top(ps['values'])} |"
            )
        lines.append("")

    # Custom/unrecognized
    if a.custom_attrs:
        lines.append("## Custom/Unrecognized Attributes")
        lines.append("")
        lines.append("*Not in default Senzing config — verify these are intentional custom features.*")
        lines.append("")
        lines.append("| Attribute | Records | Pop% | Unique | Top Values |")
        lines.append("|-----------|---------|------|--------|------------|")
        for attr in sorted(a.custom_attrs):
            cs = a.custom_attrs[attr]
            lines.append(
                f"| {attr} | {cs['count']:,} | {_pct(cs['count'], rc)} "
                f"| {len(cs['values']):,} | {_top(cs['values'])} |"
            )
        lines.append("")

    # Warnings
    warnings = []

    # FIRST, because it changes what can resolve at all: records with DIFFERENT
    # RECORD_TYPEs cannot resolve together. RECORD_TYPE partitions the datastore, so a
    # dataset carrying more than one type has split itself — deliberately or by mapper
    # accident. Buried at the bottom of a long report this gets missed, and the symptom
    # ("duplicates that never merged") looks like a matching problem rather than a
    # mapping decision.
    if len(a.record_types) > 1:
        breakdown = ", ".join(
            f"{rt} ({n:,})" for rt, n in sorted(a.record_types.items(), key=lambda kv: -kv[1])
        )
        warnings.append(
            f"RECORD_TYPE: {len(a.record_types)} DIFFERENT types present — {breakdown}. "
            "RECORDS WITH DIFFERENT RECORD_TYPEs CANNOT RESOLVE TOGETHER. Senzing will not "
            "merge a PERSON with an ORGANIZATION no matter how well the names and addresses "
            "agree. If these types are intentional (genuinely different kinds of thing), this "
            "is correct and nothing needs changing. If the same real-world entity appears "
            "under more than one type — or a mapper assigned types inconsistently — those "
            "records will stay permanently unresolved and will look like duplicates Senzing "
            "failed to catch."
        )
    if a.uncommon_record_types:
        items = ", ".join(
            f"{rt!r} (first at line {ln})" for rt, ln in sorted(a.uncommon_record_types.items())
        )
        warnings.append(
            f"RECORD_TYPE: value(s) outside the common set: {items}. This is ALLOWED — the "
            "Entity Specification does not require registering RECORD_TYPE values, and "
            f"conventions beyond {sorted(COMMON_RECORD_TYPES)} are fine. Two things to check: "
            "(1) it must be an INTRINSIC type (what the thing IS), never a role label like "
            "EMPLOYEE/VENDOR/CUSTOMER — role labels split entities that should resolve; "
            "(2) it partitions resolution, so every record of the same real-world kind must "
            "use the SAME spelling."
        )

    for ftype, fs in a.feature_stats.items():
        freq = a.features.get(ftype, {}).get("freq", "")
        pop_pct = fs["count"] / rc * 100 if rc else 0
        if pop_pct < 25 and freq not in ("NONE", "FVM"):
            warnings.append(f"{ftype} population is {pop_pct:.1f}% (< 25%)")
        if fs["count"] > 0:
            uniq_pct = len(fs["values"]) / fs["count"] * 100
            if freq in ("A1", "F1") and uniq_pct < 80:
                warnings.append(f"{ftype} uniqueness is {uniq_pct:.1f}% (expected > 80% for {freq})")
            elif freq == "FF" and uniq_pct < 60:
                warnings.append(f"{ftype} uniqueness is {uniq_pct:.1f}% (expected > 60% for FF)")

    # Desired/required attr coverage
    for ftype, fs in a.feature_stats.items():
        fconfig = a.features.get(ftype, {})
        for attr, req in fconfig.get("attrs", {}).items():
            if req in ("Desired",) and fs["count"] > 10:
                attr_ct = fs.get("attrs", {}).get(attr, {}).get("count", 0)
                if attr_ct == 0:
                    warnings.append(f"{attr} (desired for {ftype}) is never populated")

    # G2: unknown feature-position keys that are likely typos of a catalog attribute. NUDGE only
    # (an unknown key is legitimately payload) — but inside FEATURES a near-miss almost certainly
    # means the intended feature is being silently lost.
    for u, (suggested, hits) in sorted(a.near_miss.items()):
        warnings.append(
            f"G2: feature key '{u}' ({hits} record(s)) is not in the catalog but closely matches "
            f"'{suggested}' — likely a typo. Senzing carries '{u}' as payload (NOT matched on); "
            f"if you meant the feature, fix the spelling to '{suggested}'."
        )

    if a.type_name_mismatches:
        n = len(a.type_name_mismatches)
        ex = "; ".join(
            f"{nm[:40]!r} (line {ln}, typed {dt}, looks {lk})"
            for ln, dt, lk, nm in a.type_name_mismatches[:5]
        )
        warnings.append(
            f"G8 person/org: {n} record(s) carry a NAME that confidently disagrees with "
            f"RECORD_TYPE (two-detector agreement; ambiguous cases abstained). Re-check the type "
            f"on these. Examples: {ex}"
        )

    if a.missing_record_id > 0:
        warnings.append(f"RECORD_ID missing in {_pct(a.missing_record_id, rc)} of records")
    if a.empty_features_records:
        n = len(a.empty_features_records)
        warnings.append(
            f"G9: {n} record(s) have an EMPTY FEATURES array (lines "
            f"{a.empty_features_records[:5]}{'…' if n > 5 else ''}) — nothing to match on. "
            "Legitimate only for payload-only records; if unexpected, the mapper is dropping "
            "features (check it reads the source's REAL field names/shapes)."
        )
    if a.qualifier_only:
        n = len(a.qualifier_only)
        _, fam, quals = a.qualifier_only[0]
        warnings.append(
            f"QUALIFIER-ONLY features in {n} record(s) (lines "
            f"{[ln for ln, _, _ in a.qualifier_only[:5]]}{'…' if n > 5 else ''}), e.g. "
            f"{quals} with no {fam} value — it qualifies nothing. "
            f"{_qualifier_hint(fam, quals)}"
        )
    if a.flat_shape_records:
        n = len(a.flat_shape_records)
        warnings.append(
            f"SCHEMA: {n} record(s) have no FEATURES array (lines "
            f"{a.flat_shape_records[:5]}{'…' if n > 5 else ''}). This is the FLAT shape, which "
            "the Entity Specification STILL SUPPORTS — it is not an error and these records can "
            "load and resolve. The FEATURES array is the RECOMMENDED schema; migrating to it is "
            "optional. NOTE: this analyzer's feature statistics below only cover attributes it "
            "found inside FEATURES, so coverage will read low for flat records even when the "
            "engine extracts those features normally."
        )
    if a.root_feature_attrs:
        attrs = sorted(a.root_feature_attrs)
        shown = ", ".join(attrs[:8]) + ("…" if len(attrs) > 8 else "")
        types = ", ".join(sorted(a.root_feature_types)) or "none resolved"
        warnings.append(
            f"SCHEMA: {len(attrs)} registered feature attribute(s) are at the record ROOT rather "
            f"than inside FEATURES ({shown}). Supported (flat shape) — Senzing extracts these as "
            f"features, NOT as payload. Feature types found at the root: {types}. Moving them into "
            "a FEATURES array is the recommended schema but is not required."
        )
    if a.prefixed_attrs:
        ex = "; ".join(
            f"{k} -> {attr} (usage type {usage})"
            for k, (attr, _ft, usage) in sorted(a.prefixed_attrs.items())[:6]
        )
        n = len(a.prefixed_attrs)
        warnings.append(
            f"SCHEMA: {n} root attribute(s) carry the usage type as a PREFIX: {ex}"
            f"{'; …' if n > 6 else ''}. This is the flat shape's way of expressing a usage type "
            "and Senzing handles it. In the recommended FEATURES schema the same information is a "
            "separate key in the feature object (e.g. {\"NAME_TYPE\": \"PRIMARY\", \"NAME_ORG\": "
            "\"...\"}). No action required unless you are migrating to the FEATURES schema."
        )
    # Relationship reporting. The only-one-type case is handled in finalize() (an ERROR, or a recorded
    # note when waived by an explanation). The per-pointer non-match is a softer, non-conclusive signal:
    if a.rel_structure_note:
        warnings.append(a.rel_structure_note)
    if a.rel_anchor_keys and a.rel_pointers:
        # A pointer not matching an in-batch anchor is NOT conclusive in a small sample — the target
        # may be external (anchored in another load). Report as a warning to verify, never as broken.
        unmatched = sorted({k for _, k in a.rel_pointers if k not in a.rel_anchor_keys})
        if unmatched:
            warnings.append(
                f"{len(unmatched)} REL_POINTER target(s) match no REL_ANCHOR in this batch — not "
                f"conclusive in a small sample (the target may be external, anchored in another "
                f"load); verify rather than assume broken. Examples: {unmatched[:3]}"
            )
    if a.has_record_type == 0:
        warnings.append("RECORD_TYPE not found — include to prevent cross-type resolution")
    if "NAME" not in a.feature_stats:
        # `feature_stats` only counts attributes found INSIDE FEATURES. In the flat shape
        # the name lives at the root, so claiming "No NAME features found" there was
        # simply false — and it was the line that convinced a reporter their CORD data
        # was unusable. Only say it when no NAME was found in EITHER position.
        if "NAME" in a.root_feature_types:
            warnings.append(
                "NAME features are present at the record ROOT (flat shape), not inside a "
                "FEATURES array — Senzing extracts them normally. The NAME statistics below "
                "cover only the FEATURES array, so they under-report for this data."
            )
        else:
            warnings.append("No NAME features found")

    if warnings:
        lines.append("## Warnings")
        lines.append("")
        for w in warnings:
            lines.append(f"- {w}")
        lines.append("")

    return "\n".join(lines)


# ── Self-test ────────────────────────────────────────────────────────
def self_test():
    records = [
        '{"DATA_SOURCE":"CUST","RECORD_ID":"1","FEATURES":[{"RECORD_TYPE":"PERSON"},{"NAME_FULL":"John Smith"},{"SSN_NUMBER":"123-45-6789"}]}',
        '{"DATA_SOURCE":"CUST","RECORD_ID":"2","FEATURES":[{"NAME_LAST":"Doe","NAME_FIRST":"Jane"},{"ADDR_FULL":"123 Main St"},{"PHONE_NUMBER":"555-1234"}]}',
        '{"DATA_SOURCE":"CUST","RECORD_ID":"3","FEATURES":[{"NAME_ORG":"Acme Corp"},{"EMAIL_ADDRESS":"info@acme.com"}],"INDUSTRY":"Tech"}',
        '{"FEATURES":[{"NAME_FULL":"No Source"}]}',
        '{"DATA_SOURCE":"CUST","RECORD_ID":"5","NAME_FULL":"Flat Record"}',
        '{"DATA_SOURCE":"CUST","RECORD_ID":"6","FEATURES":[{"MSG_ID_NUMBER":"X123"}]}',
        '{"DATA_SOURCE":"CUST","RECORD_ID":"7","FEATURES":[{"RECORD_TYPE":"PERSON"},{"NAME_FUL":"Typo Person"}]}',
    ]
    analyzer = Analyzer(FEATURES)
    for i, line in enumerate(records, 1):
        analyzer.analyze(json.loads(line), i)
    analyzer.finalize()

    report = generate_report(analyzer, "self-test")
    print(report)

    checks = [
        ("record count", analyzer.record_count == 7),
        ("structural errors", len(analyzer.errors) >= 2),
        ("NAME feature", "NAME" in analyzer.feature_stats),
        ("payload stats", bool(analyzer.payload_stats)),
        ("custom attrs", "MSG_ID_NUMBER" in analyzer.custom_attrs),
        ("data sources", "CUST" in analyzer.data_sources),
        ("G2 near-miss", analyzer.near_miss.get("NAME_FUL", [None])[0] == "NAME_FULL"),
    ]

    # ── A9: relationship-structure integrity (cross-record) ──────────────
    def run(lines, explanation=""):
        az = Analyzer(FEATURES)
        for i, ln in enumerate(lines, 1):
            az.analyze(json.loads(ln), i)
        az.finalize(explanation=explanation)
        return az

    # Pointers only, ZERO anchor anywhere -> ERROR (incomplete relationship structure).
    pointers_only = run([
        '{"DATA_SOURCE":"G","RECORD_ID":"a","FEATURES":[{"NAME_FULL":"A"},'
        '{"REL_POINTER_DOMAIN":"G","REL_POINTER_KEY":"b"}]}',
    ])
    a9_pointers = any("ZERO REL_ANCHOR" in m for _, m in pointers_only.errors)

    # Anchors only, ZERO pointer anywhere -> ERROR.
    anchors_only = run([
        '{"DATA_SOURCE":"G","RECORD_ID":"b","FEATURES":[{"NAME_FULL":"B"},'
        '{"REL_ANCHOR_DOMAIN":"G","REL_ANCHOR_KEY":"b"}]}',
    ])
    a9_anchors = any("ZERO REL_POINTER" in m for _, m in anchors_only.errors)

    # BOTH present, pointer matches an in-batch anchor -> OK (no A9 error, no unmatched warning).
    both_ok = run([
        '{"DATA_SOURCE":"G","RECORD_ID":"b","FEATURES":[{"NAME_FULL":"B"},'
        '{"REL_ANCHOR_DOMAIN":"G","REL_ANCHOR_KEY":"b"}]}',
        '{"DATA_SOURCE":"G","RECORD_ID":"a","FEATURES":[{"NAME_FULL":"A"},'
        '{"REL_POINTER_DOMAIN":"G","REL_POINTER_KEY":"b"}]}',
    ])
    a9_both_no_struct_error = not any("anywhere" in m for _, m in both_ok.errors)
    both_ok_report = generate_report(both_ok, "self-test")
    a9_both_no_unmatched = "match no REL_ANCHOR" not in both_ok_report

    # Only-one-type WAIVED by an explicit explanation -> ACCEPTED (no error, recorded note).
    waived = run([
        '{"DATA_SOURCE":"G","RECORD_ID":"b","FEATURES":[{"NAME_FULL":"B"},'
        '{"REL_ANCHOR_DOMAIN":"G","REL_ANCHOR_KEY":"b"}]}',
    ], explanation="anchors enable inbound matching; targets loaded separately")
    a9_explanation = (
        not any("ZERO REL_POINTER" in m for _, m in waived.errors)
        and "ACCEPTED with explanation" in waived.rel_structure_note
    )

    # BOTH present but pointer matches NO in-batch anchor -> WARNING only, never an error.
    unmatched = run([
        '{"DATA_SOURCE":"G","RECORD_ID":"b","FEATURES":[{"NAME_FULL":"B"},'
        '{"REL_ANCHOR_DOMAIN":"G","REL_ANCHOR_KEY":"b"}]}',
        '{"DATA_SOURCE":"G","RECORD_ID":"a","FEATURES":[{"NAME_FULL":"A"},'
        '{"REL_POINTER_DOMAIN":"G","REL_POINTER_KEY":"external_target"}]}',
    ])
    unmatched_report = generate_report(unmatched, "self-test")
    a9_unmatched_warning = (
        "match no REL_ANCHOR" in unmatched_report
        and not any("anywhere" in m for _, m in unmatched.errors)
    )

    # G9: a per-record empty FEATURES is a warning; a >50% featureless batch is an error.
    g9_one_empty = run([
        '{"DATA_SOURCE":"G","RECORD_ID":"1","FEATURES":[{"NAME_FULL":"Has Feature"}]}',
        '{"DATA_SOURCE":"G","RECORD_ID":"2","FEATURES":[]}',
    ])
    g9_warns = (
        len(g9_one_empty.empty_features_records) == 1
        and not any("G9:" in m for _, m in g9_one_empty.errors)
    )
    g9_mostly_empty = run([
        '{"DATA_SOURCE":"G","RECORD_ID":"1","FEATURES":[]}',
        '{"DATA_SOURCE":"G","RECORD_ID":"2","FEATURES":[]}',
        '{"DATA_SOURCE":"G","RECORD_ID":"3","FEATURES":[{"NAME_FULL":"X"}]}',
    ])
    g9_errors = any("G9:" in m for _, m in g9_mostly_empty.errors)

    # ── G10: feature fragmentation (parts of one feature across objects) ──
    # Positive: the exact shape that produced 86 entities against an 85-entity answer key.
    frag_name = run([
        '{"DATA_SOURCE":"T","RECORD_ID":"1","FEATURES":[{"RECORD_TYPE":"PERSON"},'
        '{"NAME_FIRST":"John"},{"NAME_LAST":"Smith"}]}',
    ])
    g10_name = any("FRAGMENTED NAME" in m for _, m in frag_name.errors)

    frag_addr = run([
        '{"DATA_SOURCE":"T","RECORD_ID":"2","FEATURES":['
        '{"ADDR_FULL":"123 Main St, Springfield IL"},{"ADDR_COUNTRY":"US"}]}',
    ])
    g10_addr = any("FRAGMENTED ADDRESS" in m for _, m in frag_addr.errors)

    frag_phone = run([
        '{"DATA_SOURCE":"T","RECORD_ID":"3","FEATURES":['
        '{"PHONE_NUMBER":"555-1234"},{"PHONE_TYPE":"MOBILE"}]}',
    ])
    g10_phone = any("FRAGMENTED PHONE" in m for _, m in frag_phone.errors)

    # Negative: ADDR_FULL and ADDR_COUNTRY TOGETHER is valid (the canonical truth set
    # does exactly this in 4 of its 159 records) — no error of any kind.
    addr_full_country = run([
        '{"DATA_SOURCE":"T","RECORD_ID":"4","FEATURES":[{"ADDR_TYPE":"HOME",'
        '"ADDR_FULL":"1 A St, Singapore","ADDR_COUNTRY":"Singapore"}]}',
    ])
    g10_full_country_ok = not addr_full_country.errors

    # Negative: genuinely multi-valued features are NOT fragmentation.
    multi_valued = run([
        # Two addresses, disjoint shapes, distinguished by usage type.
        '{"DATA_SOURCE":"T","RECORD_ID":"5","FEATURES":[{"ADDR_TYPE":"HOME",'
        '"ADDR_FULL":"1 A St, X"},{"ADDR_TYPE":"MAILING","ADDR_LINE1":"PO Box 9",'
        '"ADDR_CITY":"Y"}]}',
        # Two full names in the same shape — they share content attributes.
        '{"DATA_SOURCE":"T","RECORD_ID":"6","FEATURES":[{"NAME_FIRST":"Jo",'
        '"NAME_LAST":"Ann"},{"NAME_FIRST":"Jane","NAME_LAST":"Doe"}]}',
        # Two phones, both carrying PHONE_NUMBER.
        '{"DATA_SOURCE":"T","RECORD_ID":"7","FEATURES":[{"PHONE_NUMBER":"555-1"},'
        '{"PHONE_NUMBER":"555-2","PHONE_TYPE":"WORK"}]}',
    ])
    g10_multi_ok = not any("FRAGMENTED" in m for _, m in multi_valued.errors)

    # Lone qualifier: a constant emitted as its own object with no value of its feature.
    lone_qual = run([
        '{"DATA_SOURCE":"T","RECORD_ID":"8","RECORD_TYPE":"ORGANIZATION","FEATURES":['
        '{"NAME_ORG":"Acme Widgets"},{"ADDR_TYPE":"BUSINESS"}]}',
    ])
    g10_lone = lone_qual.qualifier_only == [(1, "ADDRESS", ["ADDR_TYPE"])] and not any(
        "QUALIFIER-ONLY" in m for _, m in lone_qual.errors
    )
    g10_lone_hint = "Add ADDR_TYPE to the existing ADDRESS object" in generate_report(
        lone_qual, "self-test"
    )
    # The fragment hint names the qualifier to move when one side is qualifiers-only.
    g10_phone_hint = any(
        "Add PHONE_TYPE to the existing PHONE object" in m for _, m in frag_phone.errors
    )
    # Long-form legal suffixes are organization signals, like their abbreviations.
    org_long = all(
        person_org_signals(n, set())[1]
        for n in ("ACME Widgets Incorporated", "Acme Corporation", "Acme Limited")
    )

    # G4: NAME_ORG next to a person name attribute is an error whose advice never assumes
    # RECORD_TYPE values (they are the user's own strings) and states the either/or rule.
    g4_mixed = run([
        '{"DATA_SOURCE":"T","RECORD_ID":"9","FEATURES":[{"RECORD_TYPE":"VEHICLE"},'
        '{"NAME_ORG":"Acme Widgets"},{"NAME_FULL":"Ada Lovelace"}]}',
    ])
    g4_msgs = [m for _, m in g4_mixed.errors if m.startswith("NAME_ORG cannot co-exist")]
    g4_advice = (
        len(g4_msgs) == 1
        and "never both" in g4_msgs[0]
        and "type_discriminator" in g4_msgs[0]
        and "RECORD_TYPE decide" not in g4_msgs[0]
    )

    checks += [
        ("G4 NAME_ORG + person name -> one error, no RECORD_TYPE-values advice", g4_advice),
        ("G10 lone ADDR_TYPE (no address) -> warning, not error", g10_lone),
        ("G10 lone qualifier warning carries the add-to-object hint", g10_lone_hint),
        ("G10 orphaned PHONE_TYPE hint names the qualifier", g10_phone_hint),
        ("org terms include INCORPORATED / CORPORATION / LIMITED", org_long),
        ("G10 split NAME parts -> error", g10_name),
        ("G10 split ADDR_COUNTRY -> error", g10_addr),
        ("G10 orphaned PHONE_TYPE -> error", g10_phone),
        ("G10 ADDR_FULL + ADDR_COUNTRY together -> no error", g10_full_country_ok),
        ("G10 multi-valued features -> no error", g10_multi_ok),
    ]

    checks += [
        ("A9 pointers-only -> error", a9_pointers),
        ("A9 anchors-only -> error", a9_anchors),
        ("A9 both present -> no structure error", a9_both_no_struct_error),
        ("A9 both matched -> no unmatched warning", a9_both_no_unmatched),
        ("A9 explanation -> accepted (no error)", a9_explanation),
        ("A9 unmatched pointer -> warning only", a9_unmatched_warning),
        ("G9 one empty -> warning only", g9_warns),
        ("G9 majority empty -> error", g9_errors),
    ]

    ok = all(v for _, v in checks)
    for name, passed in checks:
        print(f"  {'PASS' if passed else 'FAIL'}: {name}")
    print(f"\n{'PASS' if ok else 'FAIL'}: self-test {'passed' if ok else 'failed'}")
    return 0 if ok else 1


# ── Main ─────────────────────────────────────────────────────────────
def main():
    if "--self-test" in sys.argv:
        return self_test()
    if "--help" in sys.argv or "-h" in sys.argv:
        print(__doc__.strip())
        return 0

    features, config_source = load_config()
    analyzer = Analyzer(features)

    # Collect input file arguments (positional args, excluding flag values)
    input_files = []
    skip_next = False
    for a in sys.argv[1:]:
        if skip_next:
            skip_next = False
            continue
        if a in ("-o", "--config", "--rel-explanation"):
            skip_next = True
            continue
        if a.startswith("-"):
            continue
        input_files.append(a)

    line_num = 0
    json_errors = 0

    def process_lines(source):
        nonlocal line_num, json_errors
        first = True
        for line in source:
            line = line.strip()
            if not line:
                continue
            if first:
                first = False
                # A leading '[' means the file is a JSON array (often pretty-printed across
                # lines), not JSONL. Coach toward JSONL and stop — the rest are array fragments
                # that would parse-fail into noise. (A valid JSONL record never starts with '['.)
                if line[:1] == "[":
                    line_num += 1
                    analyzer._error(
                        line_num,
                        "Output is a JSON array, not JSONL. Senzing needs JSONL — one compact JSON "
                        "object PER LINE (no '[' ']' brackets, no pretty-printing). Re-emit each "
                        "record on its own line.",
                    )
                    return
            line_num += 1
            try:
                record = json.loads(line)
            except json.JSONDecodeError as e:
                json_errors += 1
                if json_errors <= 10:
                    print(f"JSON error line {line_num}: {e}", file=sys.stderr)
                continue
            analyzer.analyze(record, line_num)
            if line_num % 100000 == 0:
                print(f"{line_num:,} records processed", file=sys.stderr)

    if input_files:
        for filepath in input_files:
            try:
                with open(filepath) as f:
                    process_lines(f)
            except FileNotFoundError:
                print(f"File not found: {filepath}", file=sys.stderr)
                return 2
            except IOError as e:
                print(f"Error reading {filepath}: {e}", file=sys.stderr)
                return 2
    else:
        process_lines(sys.stdin)

    if line_num == 0:
        print("No input (provide file arguments or pipe to stdin)", file=sys.stderr)
        return 2

    if json_errors:
        print(f"{json_errors} JSON parse error(s)", file=sys.stderr)

    # Optional accountable-override justification for the only-one-relationship-type error (A9).
    rel_explanation = ""
    if "--rel-explanation" in sys.argv:
        idx = sys.argv.index("--rel-explanation")
        if idx + 1 < len(sys.argv):
            rel_explanation = sys.argv[idx + 1]
    analyzer.finalize(explanation=rel_explanation)

    report = generate_report(analyzer, config_source)

    if "-o" in sys.argv:
        idx = sys.argv.index("-o")
        if idx + 1 < len(sys.argv):
            with open(sys.argv[idx + 1], "w") as f:
                f.write(report)
            print(f"Report written to {sys.argv[idx + 1]}", file=sys.stderr)
        else:
            print("-o requires a filename", file=sys.stderr)
            return 2
    else:
        print(report)

    return 1 if analyzer.errors else 0


if __name__ == "__main__":
    sys.exit(main())
