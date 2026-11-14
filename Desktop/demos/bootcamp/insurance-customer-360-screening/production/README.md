# Insurance Customer 360 and Screening

## Project Overview

Entity resolution with the Senzing SDK (Java) across six sources: two policy systems (PC_POLICY_ADMIN, LIFE_ANNUITY_ADMIN) that share no customer number, plus CREDIT_BUREAU, BUSINESS_REGISTRY, SIU_CASES and SANCTIONS_PEP. One resolution serves two uses:

- a customer 360: every P&C policy and life or annuity contract per real customer, with totals, cross-sell gaps, in-system duplicates and household or ownership links;
- a screening and due diligence queue: sanctions, SIU history, PEPs, possible matches, identity verification against credit bureau files, and ownership screening with the OFAC 50% rule and roll-up to the parent company. Every alert carries its records, match key or disclosed relationship, and list entry or SIU case.

A local web app serves both views (Overview, Customer 360, Screening & Due Diligence) alongside Senzing analysis views (entity graph, merge statistics, match keys, feature scores, cross-source overlap, search). Analyst dispositions are stored by the app in data/app/dispositions.json and are never written to Senzing. The screening rules are documented in src/query/InsuranceModel.java.

## Prerequisites

- Senzing SDK 4.x installed (macOS: Homebrew cask `senzingsdk`; Linux: packages under /opt/senzing/er).
- A JDK (17 or later; developed on OpenJDK 21). No other Java libraries are needed: JSON handling is in src/server/Json.java.
- A Senzing license sized for your record count. The built-in evaluation license is limited to 500 records.
- SQLite (the default datastore). For larger volumes, Senzing recommends PostgreSQL; see MIGRATION_CHECKLIST.md.

## Installation

```bash
cp config/engine_config.example.json config/engine_config.json   # then edit paths and license
cp .env.example .env                                            # optional, for containers
source src/scripts/senzing-env.sh
src/scripts/sz-java.sh compile src/server/*.java src/load/*.java src/validate/*.java src/query/*.java src/transform/*.java src/setup/*.java
```

Create the SQLite database file and its Senzing schema, then load the default configuration and register the data sources:

```bash
src/scripts/sz-java.sh run setup.InitDefaultConfig
src/scripts/sz-java.sh run load.RegisterDataSources
```

## Configuration

- **config/engine_config.json** holds the Senzing engine settings: the PIPELINE paths for your install, the SQL connection to the database, and the license as `LICENSEFILE` (a .lic path) or `LICENSESTRINGBASE64` (an inline key) inside PIPELINE. The env script exports it as SENZING_ENGINE_CONFIGURATION_JSON unless that variable is already set.
- **config/data_sources.yaml** is the data-source registry the loader reads: each source's `file_path` (under data/senzing-ready/) and `format`. All six sources load their whole file.
- **data/senzing-ready/** holds the Senzing-ready JSONL for the six sources. To refresh a source from its system extract, run its transformer in src/transform/ (for example `src/scripts/sz-java.sh run transform.TransformPcPolicyAdmin <extract.jsonl> data/senzing-ready/pc_policy_admin.jsonl`); the raw extracts are not part of this project and must be supplied from the source systems.

## Usage

```bash
source src/scripts/senzing-env.sh

# Load all sources (sequential, one writer thread on SQLite), then drain redo
src/scripts/sz-java.sh run load.Orchestrator

# Queries
src/scripts/sz-java.sh run query.CustomerResolution         # customer collapse and cross-line counts
src/scripts/sz-java.sh run query.Customer360                # master list -> data/query/customer_master_list.json
src/scripts/sz-java.sh run query.ScreeningQueue             # alerts -> data/query/screening_alerts.json
src/scripts/sz-java.sh run query.SearchEntities "John Smith"
src/scripts/sz-java.sh run query.DiscoverDemo how 400030     # why-records, how, network, path, hub-path

# Web app on http://localhost:8091 (export-stream build plus the business tabs)
src/scripts/sz-java.sh run server.VizServer --export --business --port 8091 --title "Insurance Customer 360 and Screening"
```

In production, run redo continuously as its own process (`src/scripts/sz-java.sh run load.RedoDrainer` is the batch form) rather than only after a load.

## Project Structure

```text
config/                 data_sources.yaml, engine_config.example.json
data/senzing-ready/     Senzing-ready JSONL per source
database/               SQLite database location (empty in the repository)
src/scripts/            senzing-env.sh (environment), sz-java.sh (compile and run)
src/transform/          per-source transformers and the shared FeatureListTransformer
src/load/               loader, orchestrator, redo drainer, data-source registration
src/validate/           export statistics and entity evidence
src/query/              query model (screening rules) and query programs
src/server/             web app (VizServer, BusinessTabs, page resources, vendored D3), Json helper
src/setup/              engine configuration and connection checks
.env.example, docker-compose.yml, MIGRATION_CHECKLIST.md
```
