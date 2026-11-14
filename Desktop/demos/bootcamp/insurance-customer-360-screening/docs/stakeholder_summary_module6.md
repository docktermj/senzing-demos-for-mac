# Stakeholder Summary — Data processing

**Date:** 2026-11-14 · **Project:** insurance customer 360 and screening

## What was done

All six data sources — P&C policies, life and annuity contracts, the credit bureau header, the business registry, SIU
cases and the sanctions/PEP list — were loaded into Senzing: 248 records, none failed, every count reconciled.

## What Senzing found

- **Customer 360:** 179 policy records belong to **127 real customers**; **38 of them hold both P&C cover and a life or
  annuity contract**, which neither policy system can see on its own.
- **Screening:** every watchlist entry resolved to someone in the data. A sanctioned individual (listed in Cyrillic) is a
  P&C policyholder **and** the 100% owner of a business that is also a P&C customer — the case name screening alone
  misses. A second sanctioned name (Arabic script) is that business's president. Two politically exposed persons each
  own 50% of a corporate customer group.
- **Fraud:** five people in SIU case files are existing policyholders, one under an alias with a different address and
  one with two passport numbers.
- **Common names are safe:** records sharing only a common name (Robert Smith, John Smith) stay separate and go to an
  analyst as possible matches, never as hits.

## Quality

5 of 5 success-criteria tests and 10 of 10 spot-checks passed; all 63 cross-source matches were reviewed with no wrong
merges found. Four matches are flagged for analyst attention.

## Decisions and next step

- Business users were not involved in this round; results are guide-validated, not stakeholder-signed.
- Next: **Query, Visualize and Discover** — the analyst-facing app (Overview, Customer 360, Screening & Due Diligence)
  that shows these results with their evidence.
