**File Type:** jsonl
**Root Records:** 1500
**Total Fields:** 8

## Structure Variations

**Distinct field patterns:** 30 (across 1,500 records)
**Common fields:** opened_on, ticket_id, topic
**Hint:** A 100% populated field with low cardinality (few unique values) likely indicates record type.

| # | Records | Fields unique to this pattern |
|---|---------|-------------------------------|
| 1 | 172 (11%) | city, phone, postal_code, requester_email, requester_name |
| 2 | 160 (11%) | postal_code, requester_email, requester_name |
| 3 | 159 (11%) | phone, postal_code, requester_email, requester_name |
| 4 | 141 (9%) | city, postal_code, requester_email, requester_name |
| 5 | 140 (9%) | city, phone, requester_email, requester_name |
| 6 | 138 (9%) | phone, requester_email, requester_name |
| 7 | 137 (9%) | requester_email, requester_name |
| 8 | 124 (8%) | city, requester_email, requester_name |
| 9 | 44 (3%) | city, postal_code, requester_name |
| 10 | 40 (3%) | postal_code, requester_name |
| ... | 245 (16%) | (20 more patterns) |

To profile each structure separately, re-run with: `--group_by <field>`

## Schema: support_tickets

**Record Count:** 1500
**Field Count:** 8

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | ticket_id | str | 1500 | 100.0% | 1500 | 100.0% | root (1500) | T000362 (1) | T001493 (1) | T001284 (1) | T000458 (1) | T000151 (1) |
| 2 | requester_name | str | 1458 | 97.2% | 1337 | 91.7% | root (1500) | Sarah Mitchell (4) | Sofia White (4) | Carol Nelson (3) | Michael Adams (3) | Betty Parker (3) |
| 3 | topic | str | 1500 | 100.0% | 10 | 0.7% | root (1500) | delivery delay (166) | loyalty points (162) | billing question (162) | product defect (157) | password reset (155) |
| 4 | opened_on | str | 1500 | 100.0% | 1146 | 76.4% | root (1500) | 2019-05-08 (5) | 2025-10-08 (4) | 2022-11-03 (4) | 2021-05-05 (4) | 2022-06-26 (4) |
| 5 | requester_email | str | 1207 | 80.5% | 1207 | 100.0% | root (1500) | sofia.ortiz.4661@example.org (1) | sarah.cruz.6541@example.org (1) | donald.allen.6677@example.org (1) | richard.flores.4327@mailtest.example (1) | deborah.nguyen.6791@mailtest.example (1) |
| 6 | phone | str | 766 | 51.1% | 766 | 100.0% | root (1500) | 206-211-6536 (1) | 206-216-3941 (1) | 2062166882 (1) | 404-216-9304 (1) | 213-211-8785 (1) |
| 7 | city | str | 751 | 50.1% | 25 | 3.3% | root (1500) | Madison (38) | Auburn (38) | Clinton (37) | Plymouth (35) | Georgetown (35) |
| 8 | postal_code | str | 806 | 53.7% | 676 | 83.9% | root (1500) | 41191 (4) | 12421 (3) | 37090 (3) | 53777 (3) | 12465 (3) |
