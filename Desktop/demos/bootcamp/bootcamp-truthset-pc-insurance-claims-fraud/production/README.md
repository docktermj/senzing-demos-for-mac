# Insurance Claims Resolution Project

## Project Overview

This Java project combines policyholder, vendor-registry, and SIU-watchlist records for a fictional property-and-casualty insurer. Its purpose is to support a trusted view of policyholders and surface evidence-backed connections for human SIU review. A possible connection is an investigation lead, not a fraud conclusion. No downstream integration system has been identified.

## Prerequisites

- Java 17 or later and Maven.
- A Senzing SDK installation available to the Java runtime. The source project used SDK 4.3.2; validate compatibility before upgrading.
- A valid Senzing engine configuration and license supplied through your deployment environment.
- SQLite is configured for local development. Select and configure a production database before deployment.

## Installation

1. Copy `.env.example` to `.env` and replace the placeholders with your installation's complete engine configuration and license path.
2. Set `SENZING_ROOT` if it cannot be discovered automatically. On macOS, `src/scripts/senzing-env.sh` attempts Homebrew discovery.
3. Load the environment and compile:

   ```bash
   source src/scripts/senzing-env.sh
   mvn package
   ```

The Maven build uses `${SENZING_ROOT}/sdk/java/sz-sdk.jar` and writes compiled classes and runtime dependencies under `target/`.

## Configuration

`config/data_sources.yaml` is the projected source registry and records each source's `file_path` and `format`. The three mapped inputs are included under `data/senzing-ready/`. No source was loaded as an evaluation subset.

The copied `Orchestrator` currently accepts explicit `CODE=JSONL_PATH` arguments; it does not parse the YAML registry. Keep those arguments aligned with the registry until the loader is updated to consume it. The four `test_only` sources (`VERIFY`, `CUSTOMERS`, `REFERENCE`, and `WATCHLIST`) point to `src/system_verification/` files that are not included here; remove those entries or provide the files before using them.

`SENZING_ENGINE_CONFIGURATION_JSON` is read by the Java programs. Keep the actual license out of version control; `SENZING_LICENSE_FILE` is a convenient path reference and must match the license setting in the engine JSON. The engine configuration may instead carry `PIPELINE.LICENSESTRINGBASE64` for an injected base64 license. `DATABASE_URL` is a deployment convention; the current Java code configures its database through the engine settings.

## Usage

After configuring and building the project:

```bash
./src/scripts/run-java.sh configuration.RegisterDataSources
./src/scripts/run-java.sh Orchestrator \
  VENDOR_REGISTRY=data/senzing-ready/VENDOR_REGISTRY.jsonl \
  SIU_WATCHLIST=data/senzing-ready/SIU_WATCHLIST.jsonl \
  POLICYHOLDERS=data/senzing-ready/POLICYHOLDERS.jsonl
./src/scripts/run-java.sh redo.ProcessRedo
```

Run a query program with `./src/scripts/run-java.sh FindVendorConnections`. The copied query utilities are examples built around the three included source files. Review their arguments and output before adapting them to operational workflows.

`docker-compose.yml` is a local template with one application service and a persistent SQLite volume. Replace its placeholder image with a built application image and keep its engine configuration and license supplied securely.

## Project Structure

```text
config/data_sources.yaml       Source path and format registry
data/senzing-ready/            Mapped JSONL inputs
database/                      Local database mount point
src/load/                      Registration, loading, redo, and validation
src/query/                     Query and review utilities
src/scripts/                   Environment and Java launch helpers
src/transform/                 Shared JSON support
pom.xml                        Maven build definition
MIGRATION_CHECKLIST.md          Production-readiness work items
```
