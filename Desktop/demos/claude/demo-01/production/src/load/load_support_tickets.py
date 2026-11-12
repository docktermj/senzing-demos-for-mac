#! /usr/bin/env python3
"""Load SUPPORT_TICKETS into Senzing. Production loader: see src/load/loader_core.py for architecture and concurrency.

Usage: python3 src/load/load_support_tickets.py data/senzing-ready/support_tickets.jsonl [--resume] [--limit N]
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from loader_core import main  # noqa: E402

if __name__ == "__main__":
    main("SUPPORT_TICKETS", Path(__file__).stem)
