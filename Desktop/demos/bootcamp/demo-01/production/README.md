# Customer 360 entity resolution

## Project Overview

Resolves the same customers held in three systems — a CRM contact list, a web-store order history and a support-ticket desk — into one trustworthy customer list with Senzing. The pipeline maps each source to Senzing JSON, loads it, and answers the business questions: how many real customers there are, which ones appear in more than one system, and why any two records were or were not resolved together.

Java (JDK 21+) on the Senzing SDK 4.x Java binding, with SQLite for development. No external Java libraries: JSON is handled by the dependency-free `src/server/Json.java`.

## Prerequisites

- Senzing SDK 4.x installed (macOS: Homebrew cask; Linux: the Senzing apt/yum packages), including the Java binding `sz-sdk.jar`.
- A JDK 21 or newer.
- A Senzing license for more than 500 records (see Configuration).
- A Senzing repository database: SQLite for development, PostgreSQL for production (see `MIGRATION_CHECKLIST.md`).

## Installation

```bash
src/scripts/build.sh
```

This sources `src/scripts/senzing-env.sh` (Senzing and JDK paths) and compiles everything into `src/build/classes`. Run programs with `src/scripts/run-java.sh <MainClass> [args]`, which sets the native-library path before starting the JVM.

## Configuration

- **Engine configuration:** set `SENZING_ENGINE_CONFIGURATION_JSON` (see `.env.example`), or place the same JSON in `config/engine_config.json` (not committed). It names the Senzing paths, the license (`LICENSEFILE` or `LICENSESTRINGBASE64` inside `PIPELINE`) and the database connection.
- **Data source registry:** `config/data_sources.yaml` is the registry the loader's sources come from, holding each source's `file_path` and `format`: CRM_CONTACTS, WEB_ORDERS and SUPPORT_TICKETS, all Senzing JSON Lines under `data/senzing-ready/`.
- **Raw source exports are not included.** The mappers in `src/transform/` read the original exports, which you supply under `data/raw/`: `crm_contacts.csv`, `web_orders.csv` and `support_tickets.jsonl`. The load-ready output they produce is already in `data/senzing-ready/`.
- **Concurrency:** the loader runs one writer by default because SQLite does not support concurrent writes. On PostgreSQL raise `--threads` (start at 2–8 per CPU core and tune to the database).

## Usage

```bash
# 1. Map the raw exports to Senzing JSON (only when the exports change)
src/scripts/run-java.sh TransformCrmContacts data/raw/crm_contacts.csv -o data/senzing-ready/crm_contacts.jsonl
src/scripts/run-java.sh TransformWebOrders data/raw/web_orders.csv -o data/senzing-ready/web_orders.jsonl
src/scripts/run-java.sh TransformSupportTickets data/raw/support_tickets.jsonl -o data/senzing-ready/support_tickets.jsonl

# 2. Register the data source codes (safe to re-run)
src/scripts/run-java.sh RegisterDataSources

# 3. Load: one source, or all of them in order, then drain the redo queue
src/scripts/run-java.sh LoadCustomerData data/senzing-ready/crm_contacts.jsonl
src/scripts/run-java.sh Orchestrator --source CRM_CONTACTS=data/senzing-ready/crm_contacts.jsonl \
    --source WEB_ORDERS=data/senzing-ready/web_orders.jsonl --source SUPPORT_TICKETS=data/senzing-ready/support_tickets.jsonl
src/scripts/run-java.sh ProcessRedo

# 4. Query
src/scripts/run-java.sh CustomerReports                        # counts, master list, cross-system list, duplicates -> data/results/
src/scripts/run-java.sh CustomerSearch --name "First Last" --email someone@example.com
src/scripts/run-java.sh ExplainMatch CRM_CONTACTS <id> WEB_ORDERS <id>
src/scripts/run-java.sh ExplainHow <entity id>
src/scripts/run-java.sh ExplainNetwork path <entity id> <entity id>
```

Failed records are logged to `logs/loading_errors.json`; retryable ones to `logs/retry-<file>`. Re-sending a record with the same DATA_SOURCE and RECORD_ID replaces it, so loads and retries are idempotent.

## Project Structure

```text
config/data_sources.yaml     source registry (file_path, format)
data/senzing-ready/          load-ready Senzing JSON Lines, one file per source
database/                    SQLite repository location (development only)
src/transform/               mappers (shared Records.java reader, Mapper.java helper)
src/load/                    RecordLoader.java engine, LoadCustomerData, Orchestrator, ProcessRedo, RegisterDataSources
src/query/                   CustomerReports, CustomerSearch, ExplainMatch, ExplainHow, ExplainNetwork, DiscoverPatterns
src/server/Json.java         dependency-free JSON reader/writer
src/scripts/                 senzing-env.sh, run-java.sh, build.sh
.env.example                 configuration template
docker-compose.yml           single-service SQLite compose file (development)
MIGRATION_CHECKLIST.md       steps before production
```
