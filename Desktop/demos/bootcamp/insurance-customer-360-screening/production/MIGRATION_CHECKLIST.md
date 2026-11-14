# Migration Checklist

⚠️ Some production topics (performance, security, monitoring, deployment) are not covered in depth during the bootcamp: complete these items before deploying.

## Database

- [ ] ⚠️ Decide on SQLite or PostgreSQL for production volume; SQLite has no concurrent writers and suits small datasets only.
- [ ] Create the production database and Senzing schema; set SQL.CONNECTION in config/engine_config.json.
- [ ] Load the default configuration and register the six data sources (setup.InitDefaultConfig, load.RegisterDataSources).
- [ ] Schedule database backups and test a restore.

## Security

- [ ] ⚠️ Keep .env, config/engine_config.json and license files out of source control (already in .gitignore).
- [ ] ⚠️ Store the license key and database credentials in a secret store, not in files on shared hosts.
- [ ] ⚠️ The web app binds 127.0.0.1 only and has no authentication; add authentication and authorization before exposing it to analysts.
- [ ] ⚠️ Review access to data/app/dispositions.json (analyst decisions) and to SIU and screening data.

## Licensing

- [ ] Obtain a production Senzing license sized for your record count (the evaluation license allows 500 records).
- [ ] Wire it into PIPELINE as LICENSEFILE or LICENSESTRINGBASE64 and confirm the engine reports the expected record limit.
- [ ] Record the license expiry date and set a renewal reminder.

## Performance

- [ ] ⚠️ Replace `*_DEFAULT_FLAGS` composites in `production/src/` with the explicit `SZ_*` flags whose output your code actually consumes — **except the export call**, where `SZ_ENTITY_DEFAULT_FLAGS` is the documented choice and hand-assembling `SZ_ENTITY_INCLUDE_*` members has been observed to drop `RELATED_ENTITIES` entirely, with no error. Senzing documents the DEFAULT composites as intended for getting started and exploration: their membership may change between Senzing versions, so pinned code can silently change what it returns after an upgrade with no error raised, and they over-fetch, costing engine work and response size. This project's query code (src/query/) and web app use them today.
- [ ] ⚠️ On PostgreSQL, raise loader and redo threads (Senzing's starting guidance is 2–8 per CPU core) and consider loading sources in parallel.
- [ ] ⚠️ Shuffle input before large loads to avoid lock contention.
- [ ] ⚠️ Size hardware with Senzing's capacity-planning guidance before loading production volume.

## Data

- [ ] Set up extracts from the two policy systems, the credit bureau feed, the business registry, the SIU case system and the screening vendor list.
- [ ] Run each source through its transformer in src/transform/ and validate counts before loading.
- [ ] Re-run the screening queue after each load and review the possible-match queue (shared SSNs or tax IDs, name-only matches).
- [ ] Decide how often each source refreshes and whether to load incrementally with redo running continuously.

## Deployment (target: local / on-premises)

- [ ] ⚠️ Provision the host: Senzing SDK, JDK, and storage for the database and backups.
- [ ] ⚠️ Build the application image (docker-compose.yml expects APP_IMAGE) or install directly on the host.
- [ ] ⚠️ Run redo as a continuously running process, separate from loading.
- [ ] ⚠️ Add monitoring for load errors, redo queue depth and web app health.
- [ ] ⚠️ Set up CI to compile the Java sources and run the query programs against a test database.
