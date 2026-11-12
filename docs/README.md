# Senzing Bootcamp demo pages

Static web pages produced by AI assistants while running the Senzing Bootcamp demos.
Open a link below to view a page.

## Pages

| Business case                              | View          | Copilot                                                       |
| ------------------------------------------ | ------------- | ------------------------------------------------------------- |
| Banking KYC/KYB and AML (truth set)        | Mockup        | [Open](copilot/truthset-banking-kyc-kyb-aml/mockup.html)        |
| Banking KYC/KYB and AML (truth set)        | Entity review | [Open](copilot/truthset-banking-kyc-kyb-aml/entity-review.html) |
| P&C insurance and claims fraud (truth set) | Mockup        | [Open](copilot/truthset-pc-insurnce-claims-fraud/mockup.html)   |

## Views

- **Mockup:** the business user's screen for the case,
  for example a list of KYC, KYB and AML leads.
- **Entity review:** an interactive graph of resolved entities.
  Select a node to see its records, or a link to see the match evidence.

## Folder layout

Every page lives at `<assistant>/<business-case>/<view>.html`.

- `<assistant>`: the AI assistant that created the page,
  one of `chatgpt`, `claude`, `copilot` or `kiro`.
- `<business-case>`: the same folder name as the demo in the repository root,
  for example `truthset-banking-kyc-kyb-aml`.
- `<view>`: the kind of page, such as `mockup` or `entity-review`.

## Adding a page

1. Save the HTML file as `<assistant>/<business-case>/<view>.html`.
   Use an existing view name when the page shows the same thing.
1. Add a row to the [Pages](#pages) table.
   For a new assistant, add a column.
