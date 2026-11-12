# Schema hints — CRM_SYSTEM

- Mapper language: Python. Source: data/raw/crm_system.csv
- Schema crm_system: flat CSV, one record per row, header row present, 7 columns, 4,000 rows.
- crm_id: unique natural key (CRM-NNNNNN), 100% populated.
- full_name: single name field, sometimes with a middle initial; all persons.
- address: single free-text field "street, city, ST zip"; ~3% empty.
- phone: mostly 999-999-9999, minority (999) 999-9999, 999.999.9999, +1 999 999 9999; ~12% empty.
- email: lowercase; ~11% empty.
- created_date: ISO dates with ~8% MM/DD/YYYY.
- campaign: 7 low-cardinality categories.
- No nesting, no discriminator field, no join references.
