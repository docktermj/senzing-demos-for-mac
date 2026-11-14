# Security and Data Handling

- **Data sensitivity:** all six sources are synthetic test data; they contain no real people. The business
  problem states this, and the files contain invented names, identifiers and addresses.
- **Even so, treat the files as if sensitive:** `data/raw/` is excluded from version control, and the
  Senzing database (`database/`) and `licenses/` are excluded too.
- **If you later use real data:** anonymize it for testing where you can, confirm you have permission to
  use it, and keep it out of the recap, screenshots and any shared artifact.
- **Credentials:** none are used in this project. Never paste license keys or passwords into chat.
