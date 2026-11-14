# WATCHLIST transformation

- **Reads:** `data/raw/screening_list-improved.jsonl` (17 records, Senzing JSON)
- **Writes:** `data/senzing-ready/watchlist.jsonl` (17 records); sample `data/mapping/watchlist_sample.jsonl`
- **Program:** `src/transform/transform_watchlist.java` → shared `src/transform/SenzingJsonPassThrough.java`, JSON via `src/server/MiniJson.java`
- **Mapping:** `docs/mapping/WATCHLIST_mapper.md`; analyzer report `docs/mapping/WATCHLIST_analysis.md`
- **Quality:** 63.7; analyzer clean (no structural or critical errors); verbatim check passes

## How to run

```bash
source src/scripts/senzing-env.sh
javac -d data/temp/transform_classes src/server/MiniJson.java src/transform/SenzingJsonPassThrough.java src/transform/transform_watchlist.java
src/scripts/run-java.sh data/temp/transform_classes TransformWatchlist data/raw/screening_list-improved.jsonl -o data/senzing-ready/watchlist.jsonl
```
