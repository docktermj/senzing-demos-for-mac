#! /usr/bin/env python3
"""Load BILLING_SYSTEM into Senzing. Production loader: see src/load/loader_core.py for architecture and concurrency.

Usage: python3 src/load/load_billing_system.py data/senzing-ready/billing_system.jsonl [--resume] [--limit N]
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from loader_core import main  # noqa: E402

if __name__ == "__main__":
    main("BILLING_SYSTEM", Path(__file__).stem)
