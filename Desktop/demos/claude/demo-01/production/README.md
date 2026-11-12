# Customer 360 Entity Resolution

## Project Overview

This project resolves customer records from three systems (CRM_SYSTEM, BILLING_SYSTEM and SUPPORT_TICKETS) into one view of each customer with the Senzing SDK, in Python on SQLite. It produces a master list of resolved customers, a review queue of likely duplicates that were not merged, and counts of unique customers per system. The project is standalone: no downstream system is integrated.

## Prerequisites

- Linux (x86_64) with the Senzing SDK 4.x runtime installed (`/opt/senzing`).
- Python 3.12 or later. The Senzing Python packages ship with the runtime; do not install them from PyPI.
- A Senzing license sized for your production volume.

## Installation

1. Copy `.env.example` to `.env` and fill in real values.
2. Put the Senzing Python SDK on the path: `export PYTHONPATH=/opt/senzing/er/sdk/python:$PYTHONPATH`.
3. Export `SENZING_ENGINE_CONFIGURATION_JSON` from `.env`.
4. Create and initialize the Senzing database (SQLite file under `database/`, or a PostgreSQL database for production volume).

## Configuration

- `config/data_sources.yaml` is the registry the loader reads: each source's `file_path` and `format`.
- Each transformation program takes its raw source export as its input argument. Supply these files yourself (they are not included): `data/raw/crm_system.csv` for `transform_crm_system`, `data/raw/support_tickets.csv` for `transform_support_tickets`, and `data/raw/billing_system-regenerated.csv` for `transform_billing_system`.
- Mapped, load-ready files are in `data/senzing-ready/`.
- The license goes in the engine configuration's `PIPELINE` section (`LICENSESTRINGBASE64` or `LICENSEFILE`).

## Usage

Run every command from this directory, with the environment from Installation set.

- Transform: `python3 src/transform/transform_crm_system.py <raw file>` (likewise `transform_support_tickets.py`, `transform_billing_system.py`).
- Register data sources: `python3 src/load/register_data_sources.py`
- Load one source: `python3 src/load/load_crm_system.py data/senzing-ready/crm_system.jsonl` (add `--limit N` for a test load, `--resume` to continue).
- Load the remaining sources in order with reconciliation: `python3 src/load/orchestrator.py`
- Drain the redo queue: `python3 src/load/process_redo.py`
- Customers in all three systems: `python3 src/query/customers_in_all_sources.py`
- Review queue of possible duplicates: `python3 src/query/review_queue.py`
- Unique customers per system: `python3 src/query/unique_customer_counts.py`
- Inspect entities: `src/query/show_entity.py`, `sample_entities.py`, `why_pair.py`, `how_entity.py`, `network_explore.py`.
- Evaluation and audits: `src/query/record_counts.py`, `cross_source_review.py`, `match_key_audit.py`, `how_state_audit.py`, `discover_patterns.py`, `review_entities.py`.

Python programs need no build step.

## Project Structure

- `src/transform/` - source-to-Senzing transformation programs
- `src/load/` - loaders, orchestrator, data-source registration, redo processing
- `src/query/` - query, review and audit programs (`sz_export.py` is the shared engine helper)
- `data/senzing-ready/` - load-ready JSONL files
- `config/data_sources.yaml` - data-source registry
- `database/` - database location (empty)
