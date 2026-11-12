# Module 7 Query Programs

These Java programs query the resolved data through the Senzing SDK and write JSON/JSONL outputs for reporting or downstream API use. They do not query Senzing's internal database tables. Source records are identified by the `(DATA_SOURCE, RECORD_ID)` pair.

Run each program from the repository root:

```bash
./src/scripts/run-java.sh BuildMasterList
./src/scripts/run-java.sh FindPolicyholderDuplicates
./src/scripts/run-java.sh FindVendorConnections
./src/scripts/run-java.sh ScreenWatchlistLeads
./src/scripts/run-java.sh ReviewQualitySamples
./src/scripts/run-java.sh DemonstrateRelationshipNetworks
./src/scripts/run-java.sh BuildCrossSourceReviewQueue
./src/scripts/run-java.sh BuildHowMergeAudit
```

| Program | Output | Purpose |
|---|---|---|
| `BuildMasterList` | `reports/query/master_list.jsonl` | One row per resolved entity, with its distinct source records and match keys. |
| `FindPolicyholderDuplicates` | `reports/query/policyholder_duplicates.json` | Groups policyholder records that resolve to the same entity. |
| `FindVendorConnections` | `reports/query/vendor_connections.json` | Reports policyholder/vendor same-entity and related-entity links with match evidence. |
| `ScreenWatchlistLeads` | `reports/query/watchlist_leads.json` | Screens each watchlist record against policyholder and vendor entities; candidates are investigation leads, not verdicts. |
| `ReviewQualitySamples` | `reports/query/quality_samples.json` | Retrieves larger entities and possible-match explanations for manual review. |
| `DemonstrateRelationshipNetworks` | `reports/query/relationship_networks.json` | Explores a relationship hub, finds an indirect path, and records the SDK's network links and source-record relationship evidence. |
| `BuildCrossSourceReviewQueue` | `reports/query/cross_source_review_queue.json` | Scans resolved entities through the SDK for cross-source `POSSIBLY_SAME` and ambiguous pairs. |
| `BuildHowMergeAudit` | `reports/query/how_merge_audit.json` | Audits each multi-record entity's `howEntity` resolution steps and the match key for each merge. |

The current run produced 162 distinct source records across 90 entities, 36 multi-record policyholder groups, 33 policyholder/vendor links (9 same-entity and 24 related-entity), and 32 watchlist record-candidate pairs across 17 screened records.

`quality_samples.json` includes source-record details to support evidence review. Treat it as sensitive if these programs are ever run on non-synthetic data. The JSON/JSONL files are API-ready data outputs, not a deployed HTTP API.
