# CORE_BANKING transformation

- **Reads:** `data/raw/core_cif_extract.jsonl` (97 records, Senzing JSON)
- **Writes:** `data/senzing-ready/core_banking.jsonl` (97 records); sample `data/mapping/core_banking_sample.jsonl` (12)
- **Program:** `src/transform/transform_core_banking.java` → shared `src/transform/SenzingJsonPassThrough.java`, JSON via `src/server/MiniJson.java`
- **Mapping:** `docs/mapping/CORE_BANKING_mapper.md`; analyzer report `docs/mapping/CORE_BANKING_analysis.md`
- **Quality:** 73.8; analyzer clean (no structural or critical errors); verbatim check passes

## How to run

```bash
source src/scripts/senzing-env.sh
javac -d data/temp/transform_classes src/server/MiniJson.java src/transform/SenzingJsonPassThrough.java src/transform/transform_core_banking.java
src/scripts/run-java.sh data/temp/transform_classes TransformCoreBanking data/raw/core_cif_extract.jsonl -o data/senzing-ready/core_banking.jsonl
```
