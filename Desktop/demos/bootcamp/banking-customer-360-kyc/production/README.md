# Banking Customer 360 and KYC/KYB Entity Resolution

## Project Overview

This project resolves a regional bank's customer data with the Senzing SDK, from Java, so that one
resolution serves two teams:

- Relationship managers get a Customer 360: one profile per real customer across deposit accounts and cards.
- Compliance gets a KYC/KYB alert queue: watchlist screening, identity conflicts and beneficial-ownership chains, each with the records behind it.

Five sources are mapped to Senzing JSON and loaded into one entity repository:

| Data source | Senzing-ready file | Contents |
|---|---|---|
| CORE_BANKING | data/senzing-ready/core_banking.jsonl | Core banking customer information file: deposit accounts, ownership, balances |
| CARD_SERVICES | data/senzing-ready/card_services.jsonl | Cardholder master: card accounts, primary and authorized users |
| CREDIT_BUREAU | data/senzing-ready/credit_bureau.jsonl | Credit header records: identity attributes and disclosed relationships |
| BUSINESS_REGISTRY | data/senzing-ready/business_registry.jsonl | Company registrations and disclosed owners/officers |
| WATCHLIST | data/senzing-ready/watchlist.jsonl | Screening list entries with list type |

## Prerequisites

- macOS on Apple Silicon with the Senzing SDK installed through Homebrew (the paths in `src/scripts/senzing-env.sh` assume this; adjust them for another platform).
- A JDK (21 or later); the programs are compiled with `javac` against the SDK's `sz-sdk.jar`. No other Java libraries are needed.
- `sqlite3` for a SQLite repository, or a PostgreSQL database prepared by your DBA.
- A Senzing license for more than 500 records (the built-in evaluation license stops at 500).

## Installation

```bash
cp .env.example .env            # then edit DATABASE_URL and SENZING_LICENSE_FILE
source src/scripts/senzing-env.sh
```

Create the repository schema (SQLite shown; for PostgreSQL apply `szcore-schema-postgresql-create.sql` with `psql`):

```bash
sqlite3 database/G2C.db < "$SENZING_ROOT/resources/schema/szcore-schema-sqlite-create.sql"
```

Compile the programs:

```bash
javac -cp "$SZ_SDK_JAR" -d classes/setup src/verify/InitDefaultConfig.java
javac -cp "$SZ_SDK_JAR" -d classes/load src/server/MiniJson.java src/load/*.java
javac -cp "$SZ_SDK_JAR" -d classes/query src/server/MiniJson.java src/query/*.java
javac -cp "$SZ_SDK_JAR" -d classes/transform src/server/MiniJson.java src/transform/*.java
```

## Configuration

- `.env` holds `DATABASE_URL` (a Senzing connection string), `SENZING_LICENSE_FILE` and `LOG_LEVEL`. `src/scripts/senzing-env.sh` reads it and builds `SENZING_ENGINE_CONFIGURATION_JSON` unless you set that variable yourself.
- `config/data_sources.yaml` is the data-source registry: each source's `file_path` and `format`.
- The transformers in `src/transform/` read the raw source extracts, which are not part of this project. Place each system's extract under `data/raw/` (CORE_BANKING: `core_cif_extract.jsonl`, CARD_SERVICES: `card_cardholder_master.jsonl`, CREDIT_BUREAU: `credit_header.jsonl`, BUSINESS_REGISTRY: `business_registry.jsonl`, WATCHLIST: `screening_list-improved.jsonl`) and re-run the transformer for that source to refresh `data/senzing-ready/`.
- `src/load/load_source.java` defaults to one loading thread, which suits SQLite. Raise it with `--threads N` on PostgreSQL.

## Usage

Initialize the Senzing configuration once on a new repository, then register the data sources:

```bash
src/scripts/run-java.sh classes/setup configuration.InitDefaultConfig
src/scripts/run-java.sh classes/load RegisterBankingDataSources
```

Load. CREDIT_BUREAU is loaded on its own first, then the orchestrator loads the other four sources in order (BUSINESS_REGISTRY, WATCHLIST, CORE_BANKING, CARD_SERVICES), then the redo queue is drained:

```bash
src/scripts/run-java.sh classes/load LoadSource data/senzing-ready/credit_bureau.jsonl
src/scripts/run-java.sh classes/load LoadOrchestrator
src/scripts/run-java.sh classes/load ProcessRedoQueue
```

Query:

```bash
src/scripts/run-java.sh classes/query Customer360      # data/results/customer_360.json, docs/results/customer_master_list.csv
src/scripts/run-java.sh classes/query KycAlerts        # data/results/kyc_alerts.json, docs/results/kyc_alert_queue.md
src/scripts/run-java.sh classes/query Discover <mode>  # patterns | why-records | why-entities | how | network | hub-path | path
```

Refresh a source by re-running its transformer and loading the file again: records replace themselves by `DATA_SOURCE` + `RECORD_ID`.

## Project Structure

```text
.env.example            environment template (copy to .env)
docker-compose.yml      container template (SQLite, single service)
config/data_sources.yaml  data-source registry
data/senzing-ready/     Senzing JSON for the five sources
database/               the SQLite repository lives here (not committed)
src/transform/          source-to-Senzing transformers
src/load/               data-source registration, per-source loader, orchestrator, redo processor
src/query/              Customer 360, KYC/KYB alerts, discovery (why, how, network, path)
src/server/MiniJson.java  small JSON reader shared by the programs
src/verify/InitDefaultConfig.java  one-time Senzing configuration setup
src/scripts/            environment and run scripts
MIGRATION_CHECKLIST.md  work to finish before deploying
```
