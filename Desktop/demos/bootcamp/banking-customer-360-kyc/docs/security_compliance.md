# Data Handling and Security Notes

## What the data is

All five sources (248 records) are **synthetic test data**: no real people, no real accounts. Account
numbers, balances, limits and card numbers are generated, and card numbers carry only their last four
digits. The scenario still models regulated content (SSNs, dates of birth, sanctions and fraud
flags), so it is handled as if it were sensitive.

## Handling rules for this project

- Raw data lives in `data/raw/`, which `.gitignore` excludes from version control, along with
  `data/samples/`, `data/temp/`, `data/mapping/`, the Senzing database (`database/`), licence material
  (`licenses/`, `config/license.json`) and `config/engine_config.json`.
- The Senzing database is a local file at `database/G2C.db`. Treat it like the raw data.
- The scenario keeps two views apart: the customer 360 never shows watchlist, credit bureau or
  business registry data, and the compliance view never shows balances or credit limits. Later modules
  build to that rule.
- The answer key (`banking_truthset_key.csv`) stays in its source folder and is used only to validate
  results.

## If this is ever repeated with real data

Anonymize or mask PII for testing, confirm you have permission to use the data, restrict who can read
`data/raw/` and `database/`, and move the work to a database and environment that meet your
organization's controls (see the migration checklist produced at graduation).
