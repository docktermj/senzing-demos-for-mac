# BUSINESS_REGISTRY transformation

- **Reads:** `data/raw/business_registry.jsonl` (22 records, Senzing JSON)
- **Writes:** `data/senzing-ready/business_registry.jsonl` (22 records); sample `data/mapping/business_registry_sample.jsonl`
- **Program:** `src/transform/transform_business_registry.java` → shared `src/transform/SenzingJsonPassThrough.java`, JSON via `src/server/MiniJson.java`
- **Mapping:** `docs/mapping/BUSINESS_REGISTRY_mapper.md`; analyzer report `docs/mapping/BUSINESS_REGISTRY_analysis.md`
- **Quality:** 75.4; analyzer clean (no structural or critical errors); verbatim check passes

## How to run

```bash
source src/scripts/senzing-env.sh
javac -d data/temp/transform_classes src/server/MiniJson.java src/transform/SenzingJsonPassThrough.java src/transform/transform_business_registry.java
src/scripts/run-java.sh data/temp/transform_classes TransformBusinessRegistry data/raw/business_registry.jsonl -o data/senzing-ready/business_registry.jsonl
```
