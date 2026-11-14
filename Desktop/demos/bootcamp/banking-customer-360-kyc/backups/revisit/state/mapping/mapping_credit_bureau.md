# CREDIT_BUREAU transformation

- **Reads:** `data/raw/credit_header.jsonl` (30 records, Senzing JSON)
- **Writes:** `data/senzing-ready/credit_bureau.jsonl` (30 records); sample `data/mapping/credit_bureau_sample.jsonl`
- **Program:** `src/transform/transform_credit_bureau.java` → shared `src/transform/SenzingJsonPassThrough.java`, JSON via `src/server/MiniJson.java`
- **Mapping:** `docs/mapping/CREDIT_BUREAU_mapper.md`; analyzer report `docs/mapping/CREDIT_BUREAU_analysis.md`
- **Quality:** 92.1; analyzer clean once the cross-source REL_ANCHOR is explained (`--rel-explanation`); verbatim check passes

## How to run

```bash
source src/scripts/senzing-env.sh
javac -d data/temp/transform_classes src/server/MiniJson.java src/transform/SenzingJsonPassThrough.java src/transform/transform_credit_bureau.java
src/scripts/run-java.sh data/temp/transform_classes TransformCreditBureau data/raw/credit_header.jsonl -o data/senzing-ready/credit_bureau.jsonl
```
