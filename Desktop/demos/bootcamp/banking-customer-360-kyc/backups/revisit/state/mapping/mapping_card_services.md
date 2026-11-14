# CARD_SERVICES transformation

- **Reads:** `data/raw/card_cardholder_master.jsonl` (82 records, Senzing JSON)
- **Writes:** `data/senzing-ready/card_services.jsonl` (82 records); sample `data/mapping/card_services_sample.jsonl`
- **Program:** `src/transform/transform_card_services.java` → shared `src/transform/SenzingJsonPassThrough.java`, JSON via `src/server/MiniJson.java`
- **Mapping:** `docs/mapping/CARD_SERVICES_mapper.md`; analyzer report `docs/mapping/CARD_SERVICES_analysis.md`
- **Quality:** 72.4; analyzer clean (no structural or critical errors); verbatim check passes

## How to run

```bash
source src/scripts/senzing-env.sh
javac -d data/temp/transform_classes src/server/MiniJson.java src/transform/SenzingJsonPassThrough.java src/transform/transform_card_services.java
src/scripts/run-java.sh data/temp/transform_classes TransformCardServices data/raw/card_cardholder_master.jsonl -o data/senzing-ready/card_services.jsonl
```
