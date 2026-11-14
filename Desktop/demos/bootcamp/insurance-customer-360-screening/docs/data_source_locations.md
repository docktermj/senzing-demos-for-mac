# Data Source Locations

All six sources are the bootcamper's own synthetic insurance data (no real people). They were
provided by file path and copied once into `data/raw/`. The original folder, outside this project, was:
`/Users/demo/senzing-demos-for-mac/Desktop/demos/bootcamp/insurance-customer-360-screening/raw-data/`

## Data Source 1: PC_POLICY_ADMIN

- **Description**: property and casualty policy administration system (personal and commercial lines)
- **Type**: JSONL file (one record per line)
- **Location**: `data/raw/pc_policy_admin_extract.jsonl`
- **Original source**: local file path supplied by the bootcamper
- **Collected**: 2026-11-14
- **Record count**: 104 (matches the 104 stated in `docs/business_problem.md`)
- **Access method**: one-time copy of a file; no credentials involved

## Data Source 2: LIFE_ANNUITY_ADMIN

- **Description**: life and annuity administration system
- **Type**: JSONL file (one record per line)
- **Location**: `data/raw/life_annuity_admin_extract.jsonl`
- **Original source**: local file path supplied by the bootcamper
- **Collected**: 2026-11-14
- **Record count**: 75 (matches the 75 stated in `docs/business_problem.md`)
- **Access method**: one-time copy of a file; no credentials involved

## Data Source 3: CREDIT_BUREAU

- **Description**: credit header records used for identity verification
- **Type**: JSONL file (one record per line)
- **Location**: `data/raw/credit_header.jsonl`
- **Original source**: local file path supplied by the bootcamper
- **Collected**: 2026-11-14
- **Record count**: 30 (matches the 30 stated in `docs/business_problem.md`)
- **Access method**: one-time copy of a file; no credentials involved

## Data Source 4: BUSINESS_REGISTRY

- **Description**: business registrations with owners, officers and parent companies
- **Type**: JSONL file (one record per line)
- **Location**: `data/raw/business_registry.jsonl`
- **Original source**: local file path supplied by the bootcamper
- **Collected**: 2026-11-14
- **Record count**: 22 (matches the 22 stated in `docs/business_problem.md`)
- **Access method**: one-time copy of a file; no credentials involved

## Data Source 5: SIU_CASES

- **Description**: special investigations unit case file
- **Type**: JSONL file (one record per line)
- **Location**: `data/raw/siu_cases.jsonl`
- **Original source**: local file path supplied by the bootcamper
- **Collected**: 2026-11-14
- **Record count**: 13 (matches the 13 stated in `docs/business_problem.md`)
- **Access method**: one-time copy of a file; no credentials involved

## Data Source 6: SANCTIONS_PEP

- **Description**: screening vendor's sanctions and PEP list
- **Type**: JSONL file (one record per line)
- **Location**: `data/raw/sanctions_pep.jsonl`
- **Original source**: local file path supplied by the bootcamper
- **Collected**: 2026-11-14
- **Record count**: 4 (matches the 4 stated in `docs/business_problem.md`)
- **Access method**: one-time copy of a file; no credentials involved
