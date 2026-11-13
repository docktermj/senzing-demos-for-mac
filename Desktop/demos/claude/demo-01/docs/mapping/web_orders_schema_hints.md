# Schema hints

## web_orders (data/raw/web_orders.csv)
- Flat CSV, header row, one order per row; 11 columns, no nesting, no discriminator.
- One person per row (the customer); repeat orders by the same customer have distinct order_id.
- Name in two fields: first_name, last_name.
- Address in four fields: street, city, state, zip (missing together on ~20% of rows).
- email, phone (phone often missing; a few unformatted).
- order_id is the unique natural key. order_date, order_total are transaction attributes.
- Mapper language: Java. Source path: data/raw/web_orders.csv
