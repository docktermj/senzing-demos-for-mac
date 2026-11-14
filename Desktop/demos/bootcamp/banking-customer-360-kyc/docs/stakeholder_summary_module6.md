# Data processing — stakeholder summary

**Project:** banking Customer 360 and KYC/KYB · **Date:** 2026-11-14

**What was done.** All five bank sources (core banking, card services, credit bureau, business registry, screening list) were loaded into Senzing: 248 records, none failed. A reusable, production-pattern Java loader and orchestrator now exist in `src/load/`.

**What Senzing found.**

- 248 records resolved into 157 entities; 63 of them combine records from two or more systems.
- 127 customers sit behind the 179 core banking and card records; 38 of them hold both a deposit account and a card.
- 9 entities link a screening-list entry (2 Sanctions, 2 PEP, 5 Internal Fraud) to a customer or a registered business party, including a sanctioned person who owns 100% of a registered business and is also a deposit customer.
- 18 disclosed ownership, officer and family relationships are in place for beneficial-ownership checks.

**How accurate it is.** Against the scenario's answer key: 94.2% of real-world entities resolved exactly, 97.8% of the joins Senzing made are correct, and it found 92.3% of the joins it should make. The differences are three credit-bureau files merged with a customer the key says is someone else (an identity-verification case for compliance to review), two key groupings that mix a person with a company (Senzing keeps those apart by design), and one likely answer-key error.

**Decisions and next steps.** Proceed to Query, Visualize and Discover to build the Customer 360 and KYC/KYB views. For production: PostgreSQL instead of SQLite, a multi-threaded loader, continuous redo processing and incremental loads.
