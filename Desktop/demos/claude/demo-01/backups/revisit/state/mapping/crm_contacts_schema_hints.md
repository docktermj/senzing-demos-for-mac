# Schema hints

## crm_contacts (data/raw/crm_contacts.csv)
- Flat CSV, header row, quoted fields (address contains commas); 6 columns, no nesting, no discriminator.
- One person per row; ~300 rows are second contacts for the same customer (distinct contact_id).
- Name in ONE field: full_name.
- Address in ONE free-text field: address ("street, city, ST zip"), missing ~10%.
- email, phone (phone in three formats).
- contact_id is the unique natural key. created_date is a record attribute (two formats).
- Mapper language: Java. Source path: data/raw/crm_contacts.csv
