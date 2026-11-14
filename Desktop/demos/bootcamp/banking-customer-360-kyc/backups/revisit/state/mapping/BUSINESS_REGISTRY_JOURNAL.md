# Mapping journal: BUSINESS_REGISTRY

- Mode: interactive (concise). Mapper language: Java (shared SenzingJsonPassThrough).
- Schema business_registry: 22 records, 36 fields. Structure variation = INCORPORATED present only on the 6 ORGANIZATION records.
- Plan: business_registry = master, BUSINESS_REGISTRY, dominant PERSON, type_discriminator on FEATURES.RECORD_TYPE (PERSON 16 / ORGANIZATION 6); RECORD_ID (0.92).
- Mapping: 31 feature / 2 payload / 3 ignored. INCORPORATED -> REGISTRATION_DATE on ORGANIZATION (0.85: catalog defines REGISTRATION_DATE as the incorporation date). Relationships kept verbatim (anchor key 2011 lives on record 2012). Server warning about ADDR_FULL + parts: per object they never mix (checked).
- Step 4: src/transform/transform_business_registry.java -> data/mapping/business_registry_quality.jsonl, 22 records. SenzingJsonPassThrough gained a root-to-feature option (INCORPORATED -> REGISTRATION_DATE); the four earlier outputs regenerate byte-identical. Analyzer clean (warnings only: mixed record types expected, sparse IDs, Universal Exports employer repeats; TAX_ID_TYPE listed as non-default, as for CORE/CARD). Verbatim passes. Verdict approve.
