# Schema hints

## support_tickets (data/raw/support_tickets.jsonl)
- JSON Lines, one flat object per line, all values strings; 8 keys, no nesting, no discriminator.
- Sparse: absent values are omitted keys, not nulls (30 key patterns = missing-field combinations, not record types; topic is a ticket category, not a type).
- One person (the requester) per ticket; a person may open several tickets (distinct ticket_id).
- Name in ONE field: requester_name (~3% missing; nicknames/typos).
- Location as two parts: city, postal_code (each ~50%); no street or state.
- requester_email, phone (three formats).
- ticket_id is the unique natural key. topic, opened_on are ticket attributes.
- Mapper language: Java. Source path: data/raw/support_tickets.jsonl
