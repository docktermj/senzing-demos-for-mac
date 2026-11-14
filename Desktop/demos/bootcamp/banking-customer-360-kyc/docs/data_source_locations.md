# Data Source Locations

All five sources are **synthetic test data** (no real people, no PII), supplied by the bootcamper
for the Customer 360 and KYC/KYB scenario. They were already in Senzing JSON format (JSONL, one
record per line). Mapping is checked in Data Quality, Mapping, and Transformation.

Original folder: `/Users/demo/senzing-demos-for-mac/Desktop/demos/recipes/banking-customer-360-kyc/ingredients/`
(the scenario's `data-location.md` refers to it as `~/Desktop/demos/recipes/banking-customer-360-kyc/ingredients/`).

## Data Source 1: CORE_BANKING

- **Type**: JSONL file (core banking customer information file extract)
- **Location**: `data/raw/core_cif_extract.jsonl`
- **Original Source**: copied from the folder above on 2026-11-14
- **Record Count**: 97 (PERSON 86, ORGANIZATION 8)
- **Access Method**: one-time file copy
- **Contents**: deposit accounts (checking, savings, money market, CDs) with ownership type, status, balance, branch and relationship tier

## Data Source 2: CARD_SERVICES

- **Type**: JSONL file (card platform cardholder master)
- **Location**: `data/raw/card_cardholder_master.jsonl`
- **Original Source**: copied from the folder above on 2026-11-14
- **Record Count**: 82 (PERSON 67, ORGANIZATION 12)
- **Access Method**: one-time file copy
- **Contents**: card account, last four digits of the card, primary or authorized user, product, credit limit, balance, status (including charged off) and delinquency

## Data Source 3: CREDIT_BUREAU

- **Type**: JSONL file (credit header records)
- **Location**: `data/raw/credit_header.jsonl`
- **Original Source**: copied from the folder above on 2026-11-14
- **Record Count**: 30 (PERSON 30)
- **Access Method**: one-time file copy
- **Contents**: credit header records used to verify identity

## Data Source 4: BUSINESS_REGISTRY

- **Type**: JSONL file (business registrations)
- **Location**: `data/raw/business_registry.jsonl`
- **Original Source**: copied from the folder above on 2026-11-14
- **Record Count**: 22 (ORGANIZATION 6, PERSON 16)
- **Access Method**: one-time file copy
- **Contents**: businesses with their owners, officers and parent companies, linked by disclosed relationships with ownership percentages

## Data Source 5: WATCHLIST

- **Type**: JSONL file (screening list)
- **Location**: `data/raw/screening_list.jsonl`
- **Original Source**: copied from the folder above on 2026-11-14
- **Record Count**: 17 (PERSON 17)
- **Access Method**: one-time file copy
- **Contents**: `LIST_TYPE` is Sanctions, PEP or Internal Fraud

## Total

248 records across five sources, matching `docs/business_problem.md`. Every file passed validation
(readable UTF-8 without a byte-order mark, valid JSON on every line, one data source code per file,
unique record IDs, and record count equal to the stated count); results are in `config/data_sources.yaml`.

## Not collected, on purpose

The same source folder also holds `banking_truthset_key.csv`, an answer key
(`CLUSTER_ID,DATA_SOURCE,RECORD_ID`) showing which records belong to the same real-world entity. It
is **not data to load**. It stays in the source folder and is used only to validate results after
loading. `generate.py` (the script that produced the files) and the folder's `README.md` are
also left behind.
