# User Acceptance Testing — Data Processing

## Scope and limitations

The business scenario is fictional and records no real business stakeholders, so this was a
self-directed review. All three sources were reviewed in full; the loaded counts reconcile to
their expected counts. The requested `actual_truthset_key.csv` is not present in the checkout,
so no truth-set comparison, precision, recall, or absolute match-accuracy rate can be reported.
The findings below are evidence-based review signals, not ground-truth labels.

## Operational checks

- The SDK export contained 159 records: AML_WATCHLIST 17, BUSINESS_REGISTRY 22, and
  BANK_CUSTOMERS 120. Those totals match the verified full-load results.
- Six record-key lookups (two per source) returned the same entity IDs as the export. This checks
  lookup/export consistency; it does not establish match accuracy.
- All 9 available cross-source entities were reviewed. This is fewer than the requested 15–25
  sample because the repository contains only 9.
- Five of the 49 `POSSIBLY_SAME` pairs were reviewed with SDK explanations; all five explanations
  were returned successfully.

## Cross-source entity review

| Entity | Source records reviewed | Evidence and assessment |
|---|---|---|
| 200005 | AML_WATCHLIST:1014, 1012; BANK_CUSTOMERS:1009, 1011, 1010 | All five records have a birth date of 1970-03-01, but the names split between Eddie/Edward Kusha and Eddie/Ed Knight. This is a high-priority possible over-merge; the answer key is needed to decide whether these are aliases or distinct people. |
| 400002 | BUSINESS_REGISTRY:2071; BANK_CUSTOMERS:2072 | “Universal Exports” and “Univrsl Export Inc” are similar names, and the phone values normalize to the same digits. The merge is supported by the available evidence. |
| 400004 | BUSINESS_REGISTRY:2014; BANK_CUSTOMERS:1071, 1072 | “Wang Wei,” “王伟,” and “Wei Wang” share the same birth date (1997-09-14). The identity is supported by name variation plus birth date. |
| 400005 | BUSINESS_REGISTRY:2013; BANK_CUSTOMERS:1069, 1070 | “Wang Jie,” “王杰,” and “Jie Wang” share the same birth date (1993-09-14). The identity is supported by name variation plus birth date. |
| 400010 | BUSINESS_REGISTRY:2141; BANK_CUSTOMERS:2142 | “Universal Exports” and “Singapore exports” may be an alias or subsidiary connection. The source records do not establish whether they are the same legal entity. |
| 500009 | BUSINESS_REGISTRY:2074; BANK_CUSTOMERS:2073 | “Universal Exports” and “Worldwide Exports” are plausible variants, but the available fields do not establish whether they are the same legal entity. |
| 600004 | BUSINESS_REGISTRY:2012; BANK_CUSTOMERS:2011 | “Hajah Maimunah” and “Hajah Mamunah (Jln Pisang)” are a plausible name variation, but there is no shared birth date or unique identifier to confirm the merge. |
| 600006 | BUSINESS_REGISTRY:2041; BANK_CUSTOMERS:2042 | “Autowerkz” and “Autoworks” are a plausible spelling variation, but the available fields do not independently confirm the merge. |
| 600012 | BUSINESS_REGISTRY:2151; BANK_CUSTOMERS:2152 | “Universal Exports” and “India Exports” may be an affiliate or alias. The source relationship pointer references a global parent, but the evidence does not establish that both records are the same legal entity. |

Three person/company groups have comparatively strong corroborating evidence (400002, 400004,
400005); two additional groups are plausible but unconfirmed (600004, 600006); three company
groups need a same-entity-versus-affiliate decision (400010, 500009, 600012); and entity 200005
needs review for a possible over-merge.

## Targeted iteration review: entity 200005

The highest-priority candidate was rechecked against the SDK export and the raw/load-ready
records. The export lists these record match keys within the five-record entity:

- AML_WATCHLIST:1012 — `+NAME+DOB+ADDRESS`
- BANK_CUSTOMERS:1009 — `+DOB+SSN+PNAME`
- BANK_CUSTOMERS:1011 — `+NAME+DOB+ADDRESS+PHONE`
- BANK_CUSTOMERS:1010 — `+NAME+DOB+ADDRESS`

AML_WATCHLIST:1014 is the initial record in the entity and has no per-record match key in the
export. For these five records, the inspected names and birth dates match between raw and load-ready
files; the AML_WATCHLIST:1014 and BANK_CUSTOMERS:1009 SSN values differ only in punctuation. No
transformation defect was found that explains or safely resolves the grouping. The evidence still
does not establish whether the records represent one person or more than one, so no mapping or
source-data change was made. Match accuracy remains unmeasured without the truth-set key.

## Additional over-merge review

Three single-source entities have conflicting birth dates among their merged records:

- Entity 700001 combines records with birth dates 1978-12-11, 1979-11-12, and 1978-11-12.
- Entity 700030 includes two records with birth date 11/12/73 and one with 11/21/73.
- Entity 700050 includes birth dates 10/27/76 and 10/28/76.

These are candidates for review, not confirmed false positives. The source-profile reports show
repeated names and birth dates, so the conflicts need the missing answer key or domain confirmation
before any mapping change.

## Possible-match review

The five reviewed possible matches illustrate why they remain unresolved:

| Entity pair | Relationship key | Evidence observed |
|---|---|---|
| 200001 / 200009 | `+NAME+ADDRESS+PHONE-DOB` | Names, addresses, and phones are close; birth dates conflict. |
| 200001 / 700001 | `+NAME+ADDRESS-DOB` | Name and address are similar; birth dates conflict. |
| 200001 / 700042 | `+NAME+DRLIC-DOB` | Driver-license values agree and names are similar; birth dates conflict. |
| 200002 / 500008 | `+NAME+ADDRESS-GENERATION-DOB` | Name and address are similar; generation and birth date conflict. |
| 200003 / 600007 | `+NAME+ADDRESS+PHONE-DOB` | Name, address, and phone are similar; birth dates conflict. |

These are useful analyst-review leads, but without the answer key they cannot be classified as
false negatives or confirmed distinct people.

## UAT outcome

Load and retrieval checks passed. Match-quality UAT remains **indeterminate**: the cross-source
review found both supported identities and unresolved or suspicious cases, and there is no answer
key to compute an accuracy percentage. The high relationship-level birth-date suppressor share is
reported separately in `docs/results_validation.md`; it is not treated as a match-accuracy failure.
