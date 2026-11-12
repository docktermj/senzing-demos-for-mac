# Entity Resolution Project

## Project Overview

This Java project loads and queries resolved entity data from three registered sources:
`BANK_CUSTOMERS`, `BUSINESS_REGISTRY`, and `AML_WATCHLIST`. The starter configuration targets a
local, on-premises environment and uses SQLite for development.

The copied data files are the evaluation inputs. Replace them with approved production data before
deployment, and review the migration checklist before using the project operationally.

The SQLite configuration is for development and testing, not production. Select a production
database backend before deployment and configure it in the engine settings.

## Prerequisites

- Java 25 or a compatible JDK for the copied source code.
- The Senzing SDK installed for the target operating system.
- A configured Senzing engine and valid license.
- Python 3 for the loader registry wrapper.
- Bash for the supplied launch scripts.

The included Compose service provides a Java workspace only. The Senzing SDK and its native
libraries must be installed and configured for the environment where the programs run.

## Installation

1. Install the Java SDK and configure the required engine settings for the intended environment.
2. Copy `.env.example` to `.env`, replace all placeholders, and do not commit `.env`.
3. Ensure the `SENZING_ROOT` environment variable points to the SDK installation. On macOS with
   Homebrew, the environment helper attempts to derive it from `brew --prefix`.
4. Review the inputs listed in `config/data_sources.yaml`. The loader reads that registry; each
   source entry contains its `file_path` and `format`.
5. Place the intended load-ready files at the configured paths under `data/senzing-ready/`.

The example `.env` keeps the engine configuration JSON and license path as separate inputs. The
license path is not automatically inserted into the engine JSON; configure the license under
`PIPELINE` in the engine configuration before running the programs.

## Configuration

`config/data_sources.yaml` is the source registry read by the loader. It contains the production
projection of each source's name, `file_path`, and `format`; edit it when changing source files.
The checked-in JSONL files are examples and should be replaced or approved for the intended use.

`SENZING_ENGINE_CONFIGURATION_JSON` supplies the engine configuration to Java programs.
`SENZING_LICENSE_FILE` is a convenience path in `.env.example` and requires explicit configuration
in the engine JSON, for example as `PIPELINE.LICENSEFILE`; an encoded license may instead use
`PIPELINE.LICENSESTRINGBASE64`. `DATABASE_URL` records the intended database connection for
operators; configure the engine JSON consistently with the chosen database. Keep real license
material and credentials out of source control.

## Usage

From the project root, configure the environment and run the copied query launcher:

```bash
set -a
source .env
set +a
source src/scripts/senzing-env.sh
bash src/query/run.sh <fully-qualified-main-class> [arguments...]
```

To load one or more registered sources, use the production wrapper so its temporary runtime
registry can supply the loader status fields without adding them to the checked-in projection:

```bash
bash src/load/run-orchestrator.sh BANK_CUSTOMERS BUSINESS_REGISTRY AML_WATCHLIST
```

The wrapper compiles no code and uses the copied build output. Rebuild the Java classes when source
files change, using the SDK Java archive and `src/server/target/dependency/javax.json-1.1.4.jar`.

## Project Structure

```text
config/data_sources.yaml       Source registry projection
data/senzing-ready/            Load-ready JSONL inputs
database/                      Local database mount point
src/load/                      Java loading and audit programs
src/query/                     Java search, query, and discovery programs
src/scripts/                   Environment and Java launch helpers
src/transform/                 Java data transformation programs
```
