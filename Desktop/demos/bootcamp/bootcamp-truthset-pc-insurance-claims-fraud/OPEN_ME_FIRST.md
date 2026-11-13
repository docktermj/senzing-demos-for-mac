# Open me first

This is a packaged Senzing Bootcamp project.

**The business problem it addresses:** A fictional U.S. property-and-casualty insurer needs a trusted view of policyholders and their connections to claim-paid businesses and watchlist subjects, so legitimate claims can be handled quickly and potential fraud can be referred to the special investigations unit (SIU) with supporting evidence.

## Start here

Open **`docs/bootcamp_recap.pdf`** first — it is the guided tour of what was built,
in order, with the visualizations embedded.

## What this package is

Profile **`transfer`** — everything needed to continue the bootcamp on another
machine: the results, plus the revisit bundle (state snapshot and database backup),
`config/`, the mappings and `src/`.

**To resume:** open **`docs/REVISIT_BOOTCAMP.md`** — it carries the restore and
re-initialization commands for this project's database and state.

## What is NOT here, and why

`PACKAGE_MANIFEST.json` lists every excluded path with its reason. The categories:

- **Credentials and licenses** (`.env`, `licenses/`, `config/license.json`) — never
  packaged, in either profile.
- **Your own source data** (`data/raw/`) — never packaged. It is yours, it may be
  sensitive, and the resolved results do not require it.
- **Anything matching a secret pattern** — a PEM private key, an AWS access-key ID or a
  Senzing license payload found in a file's contents excludes that file, and the
  manifest names it.
- **`.git/`** — excluded on purpose. This archive is a **snapshot, not a clone**: git
  history can carry secrets that no longer exist in the working tree. If you want the
  history, push the repository instead of relying on this package.
- **Caches and virtualenvs** (`__pycache__/`, `.venv/`, `node_modules/`, `target/`) —
  regenerated, not preserved.

## Integrity

The archive was re-opened and verified after writing, and a `.sha256` sidecar sits beside
it. `PACKAGE_MANIFEST.json` records a SHA-256 for every included file, so you can check
any single file rather than trusting the whole archive at once.

---

Packaged by the Senzing Bootcamp plugin 0.6.1 on 20261112.
