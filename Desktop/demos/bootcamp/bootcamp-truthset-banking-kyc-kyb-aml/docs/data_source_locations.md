# Data Source Locations

The collected files are derived from the Senzing Demo Truth Set; the retained input was
`src/system_verification/truthset_data.jsonl`, split by its original `DATA_SOURCE` and
relabeled to match the business problem. The original collected files remain unchanged. The active BANK_CUSTOMERS,
BUSINESS_REGISTRY, and AML_WATCHLIST paths are synthesized regenerated copies: they preserve
record IDs and collected/improved values, with additional synthetic quality fields. Their
original and improved copies remain available beside the active versions.

Source reference: <https://github.com/Senzing/truth-sets>

| Business source | Local file | Original source label | Records | Access method |
|---|---|---|---:|---|
| BANK_CUSTOMERS | `data/raw/BANK_CUSTOMERS-regenerated.jsonl` | `CUSTOMERS` | 120 | Regenerated copy; collected input at `data/raw/BANK_CUSTOMERS.jsonl` |
| BUSINESS_REGISTRY | `data/raw/BUSINESS_REGISTRY-regenerated.jsonl` | `REFERENCE` | 22 | Regenerated copy; collected input at `data/raw/BUSINESS_REGISTRY.jsonl` |
| AML_WATCHLIST | `data/raw/AML_WATCHLIST-regenerated.jsonl` | `WATCHLIST` | 17 | Regenerated copy; improved base at `data/raw/AML_WATCHLIST-improved.jsonl`, collected input at `data/raw/AML_WATCHLIST.jsonl` |

The counts match the Senzing MCP source inventory. These are synthetic demo records, not
records about real customers. Any future real data must be handled under the applicable
privacy and access requirements.
