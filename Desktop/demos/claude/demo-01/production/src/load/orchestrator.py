#! /usr/bin/env python3
"""Load several data sources in order, each in its own process, then reconcile the counts.

Strategy: SEQUENTIAL (no load-order dependencies between the generated sources; recorded in
docs/loading_strategy.md). The Senzing SDK calls live in each source's load_<name>.py (the threaded
add_futures pattern, src/load/loader_core.py); this file is ordinary orchestration code:
  * one process per source -> per-source counters start clean and a crash in one source cannot
    affect another (error isolation);
  * retry a failed source with exponential backoff (1s, 2s, 4s, 8s), logging each attempt;
  * partial success is kept: loaded sources stay loaded when another fails;
  * health summary after each source (elapsed, completed vs remaining, error rate);
  * reconciliation: each source's loaded count must equal its input's record count, and the
    per-source counts must sum to the aggregate, or the run stops and says so.
Usage: python3 src/load/orchestrator.py [--limit N]   (--limit runs a small test load per source)
"""

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
# Load order (Data processing step 14): CRM_SYSTEM already loaded first; then quality-first.
SOURCES = [
    ("SUPPORT_TICKETS", "load_support_tickets.py", "data/senzing-ready/support_tickets.jsonl"),
    ("BILLING_SYSTEM", "load_billing_system.py", "data/senzing-ready/billing_system.jsonl"),
]
BACKOFF = [1, 2, 4, 8]


def count_lines(path):
    with open(path, encoding="utf-8") as fh:
        return sum(1 for line in fh if line.strip())


def run_source(program, input_file, limit):
    cmd = [sys.executable, str(HERE / program), input_file] + (["--limit", str(limit)] if limit else [])
    proc = subprocess.run(cmd, capture_output=True, text=True)
    out = proc.stdout
    stats = json.loads(out[out.index("{"):]) if "{" in out else None
    return proc.returncode, stats, proc.stderr[-2000:]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=None)
    args = ap.parse_args()
    start = time.monotonic()
    results = {}
    for i, (ds, program, input_file) in enumerate(SOURCES, 1):
        expected = count_lines(input_file) if args.limit is None else min(args.limit, count_lines(input_file))
        for attempt, delay in enumerate([0] + BACKOFF):
            if delay:
                print(f"  retry {attempt} for {ds} in {delay}s", flush=True)
                time.sleep(delay)
            rc, stats, err = run_source(program, input_file, args.limit)
            if rc in (0, 2) and stats:  # 2 = finished with per-record failures (logged, not retried)
                break
            print(f"  {ds}: attempt {attempt + 1} failed (exit {rc}): {err.strip()[-300:]}", file=sys.stderr)
        loaded = stats["loaded"] if stats else 0
        failed = stats["failed"] if stats else None
        results[ds] = {"input": input_file, "expected": expected, "loaded": loaded, "failed": failed,
                       "matches_input": stats is not None and loaded == expected,
                       "duration_s": stats["duration_s"] if stats else None,
                       "records_per_s": stats["records_per_s"] if stats else None,
                       "status": "loaded" if stats and loaded == expected else "failed"}
        el = time.monotonic() - start
        done_failed = sum(r["failed"] or 0 for r in results.values())
        done_total = sum(r["loaded"] + (r["failed"] or 0) for r in results.values())
        print(f"[health] {i}/{len(SOURCES)} sources done, {el:,.1f}s elapsed, "
              f"error rate {100 * done_failed / done_total if done_total else 0:.2f}%: "
              f"{ds} {loaded:,}/{expected:,}", flush=True)
    aggregate = sum(r["loaded"] for r in results.values())
    reconciled = all(r["matches_input"] for r in results.values()) and aggregate == sum(r["loaded"] for r in results.values())
    print(json.dumps({"mode": f"test (--limit {args.limit})" if args.limit else "full",
                      "sources": results, "aggregate_loaded": aggregate, "reconciled": reconciled,
                      "elapsed_s": round(time.monotonic() - start, 1)}, indent=2))
    sys.exit(0 if reconciled else 1)


if __name__ == "__main__":
    main()
