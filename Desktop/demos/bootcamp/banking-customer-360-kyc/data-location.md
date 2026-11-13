# Data Location: Customer 360 and KYC/KYB for a Retail Bank

The source data is in:

```
~/Desktop/demos/recipes/banking-customer-360-kyc/ingredients/
```

All five files are synthetic test data (no real people), so they may be read and validated freely.
They are already in Senzing JSON format, one record per line (JSONL), so they need no mapping.

## Files to load

| File | Data source | Records | What it is |
|---|---|---|---|
| `core_cif_extract.jsonl` | `CORE_BANKING` | 97 | Core banking CIF extract: deposit customers and their accounts |
| `card_cardholder_master.jsonl` | `CARD_SERVICES` | 82 | Card platform cardholder master: cardholders and their cards |
| `credit_header.jsonl` | `CREDIT_BUREAU` | 30 | Credit header records for identity verification |
| `business_registry.jsonl` | `BUSINESS_REGISTRY` | 22 | Businesses, owners, officers and parent companies |
| `screening_list.jsonl` | `WATCHLIST` | 17 | Sanctions, PEP and Internal Fraud entries |

Total: **248 records**, under the 500-record Senzing evaluation limit, so no license file is needed.

Load all five together so Senzing resolves everyone across every source in one pass.

## Other files in the folder (do not load)

- `banking_truthset_key.csv`: the answer key (`CLUSTER_ID,DATA_SOURCE,RECORD_ID`) showing which
  records belong to the same real-world entity. Use it only to validate results after loading.
- `generate.py`: the script that produced the synthetic files.
- `README.md`: a description of the ingredient set.
