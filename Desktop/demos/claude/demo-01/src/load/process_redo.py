#! /usr/bin/env python3
"""Drain the Senzing redo queue once, then exit (batch drain).

From senzing/code-snippets-v4 python/redo/redo_continuous_futures.py (sdk_guide topic='redo').
SDK calls are unchanged: engine.get_redo_record(), engine.process_redo_record(record).

DEVIATION, on purpose: the snippet is the STREAMING pattern. In production, redo is handled by an
always-running redo processor that wakes, processes pending redo records, and sleeps when the queue
is empty (the snippet pauses 30 seconds and loops forever). This bootcamp step needs a BATCH drain
that terminates after a load, so an empty get_redo_record() ends the loop instead of pausing.
The fetch's return value is the loop sentinel: count_redo_records() is a full table scan per call
and is used here only once, for the before/after report (Senzing's guidance: never as a loop condition).

MAX_WORKERS is 1 because the datastore is SQLite (no concurrent writes); on PostgreSQL raise it,
as in src/load/loader_core.py.
"""

import concurrent.futures
import json
import os
import sys
import time
from pathlib import Path

from senzing import SzBadInputError, SzError, SzRetryableError, SzUnrecoverableError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
MAX_WORKERS = 1  # SQLite: single writer. PostgreSQL: raise (2-8 per CPU core to start).
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


def process_redo_record(engine, redo_record):
    engine.process_redo_record(redo_record)


def drain(engine):
    processed = errors = 0
    start = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = {}
        while True:
            while len(futures) < MAX_WORKERS and (record := engine.get_redo_record()):
                futures[executor.submit(process_redo_record, engine, record)] = record
            if not futures:
                break  # queue empty and nothing in flight: the drain is complete
            done, _ = concurrent.futures.wait(futures, return_when=concurrent.futures.FIRST_COMPLETED)
            for f in done:
                record = futures.pop(f)
                try:
                    f.result()
                except (SzBadInputError, SzRetryableError) as err:
                    errors += 1
                    print(f"\nWARN: {err.__class__.__name__} - {err}\n{record}", file=sys.stderr)
                except (SzUnrecoverableError, SzError):
                    raise
                else:
                    processed += 1
                    if processed % 100 == 0:
                        print(f"  processed {processed:,} redo records, {errors:,} errors", flush=True)
    return processed, errors, round(time.monotonic() - start, 1)


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()
    before = sz_engine.count_redo_records()
    processed, errors, secs = drain(sz_engine)
    after = sz_engine.count_redo_records()
    print(json.dumps({"redo_pending_before": before, "redo_processed": processed, "redo_errors": errors,
                      "duration_s": secs, "redo_pending_after": after, "queue_empty": after == 0}, indent=2))
    sys.exit(0 if after == 0 and errors == 0 else 2)
except SzError as err:
    print(f"\nCRITICAL: {err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
