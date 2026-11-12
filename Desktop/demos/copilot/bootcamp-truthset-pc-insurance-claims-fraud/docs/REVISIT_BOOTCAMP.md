# Returning to This Project

## Quick Start When You Return

Run these commands from the project root:

```bash
source src/scripts/senzing-env.sh
cp backups/revisit/database/G2C.db database/G2C.db
./src/scripts/run-java.sh CountRepositoryRecords
./src/scripts/run-java.sh FindVendorConnections
python3 -m http.server --bind 127.0.0.1 8000 --directory docs/visualizations
```

Open `http://127.0.0.1:8000/results_visualization.html` to view the saved results snapshot. Stop the local web server with `Ctrl-C`.

## What You Accomplished

- **Entity Resolution Concepts:** explored false positives, false negatives, ambiguous matches, principle-based matching, and relationships.
- **Discover the Business Problem:** defined a fictional insurance claims-fraud problem and its review goals.
- **SDK setup:** configured Java and the local SQLite repository, then confirmed an engine call.
- **System Verification:** tested SDK initialization, loading, resolution, read, and search behavior using synthetic records, then removed those records.
- **Truth Set visualization:** explored the synthetic Truth Set, saved a standalone visualization and six screenshots, and documented observed count and graph limitations.
- **Data collection:** collected and validated 159 synthetic records from the three approved business-source assignments.
- **Data Quality, Mapping, and Transformation:** assessed and mapped the three sources, preserving documented quality gaps and uncertain values.
- **Data processing:** loaded 162 mapped records across the three sources, drained redo processing, and documented validation and review candidates.
- **Query, Visualize and Discover:** built Java query programs, reviewed resolution evidence and relationships, created a review queue, and built the results visualization.

The recap PDF at `docs/bootcamp_recap.pdf` contains the detailed record of each module and its outputs.

## Your Business Problem and Data Sources

A fictional U.S. property-and-casualty insurer needs a trusted view of policyholders and their connections to claim-paid businesses and watchlist subjects. The goal is to support faster handling of legitimate claims and provide evidence-backed leads for human SIU review; a possible match is not a fraud conclusion.

| Source | Collected | Mapped and loaded |
|---|---:|---:|
| `POLICYHOLDERS` | 120 | 120 |
| `VENDOR_REGISTRY` | 22 | 25 |
| `SIU_WATCHLIST` | 17 | 17 |
| **Total** | **159** | **162** |

The three mapped JSONL inputs are in `data/senzing-ready/`. Source paths and formats are recorded in `config/data_sources.yaml`.

## Restore the Database

The revisit backup is `backups/revisit/database/G2C.db`. From the project root, restore it with:

```bash
cp backups/revisit/database/G2C.db database/G2C.db
```

This replaces the current local database file. The backup was made from `database/G2C.db` after the results visualization server had been stopped.

## Re-initialize and Re-run

Source the environment in the shell that will run the programs:

```bash
source src/scripts/senzing-env.sh
```

The saved engine configuration and database state are in place. Start a fresh Java process to check the repository and run a query:

```bash
./src/scripts/run-java.sh CountRepositoryRecords
./src/scripts/run-java.sh FindVendorConnections
```

To load the included sources again rather than use the restored repository, run the orchestrator and then drain redo processing:

```bash
./src/scripts/run-java.sh Orchestrator \
  VENDOR_REGISTRY=data/senzing-ready/VENDOR_REGISTRY.jsonl \
  SIU_WATCHLIST=data/senzing-ready/SIU_WATCHLIST.jsonl \
  POLICYHOLDERS=data/senzing-ready/POLICYHOLDERS.jsonl
./src/scripts/run-java.sh redo.ProcessRedo
```

The saved visualization is a static HTML snapshot. Serve it locally with:

```bash
python3 -m http.server --bind 127.0.0.1 8000 --directory docs/visualizations
```

Then open `http://127.0.0.1:8000/results_visualization.html`. The snapshot is viewable without an engine process; live engine-backed actions require starting the corresponding Java server from the project source.

## License

The saved license metadata in `backups/revisit/state/config/license.json` records an expiry date of 2027-06-12. Verify that the license is still valid before loading records again. If using a custom license, place it at `licenses/g2.lic` and ensure the engine configuration points to it; otherwise use the configured evaluation license.

## Where Things Are

- `backups/revisit/` — database backup, resume manifest, and configuration/mapping snapshots.
- `docs/bootcamp_recap.pdf` and `docs/bootcamp_recap.md` — the shareable recap and its source.
- `docs/bootcamp_recap.mp4` — the narrated graduation video.
- `docs/video/storyboard.json` — the video storyboard, if you want to render it again with `generate_recap_video.py`.
- `docs/visualizations/` — saved HTML visualizations and their screenshots.
- `docs/REVISIT_BOOTCAMP.md` — this return guide.
