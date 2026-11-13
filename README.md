# senzing-demos-for-mac

Everything needed to run Senzing Bootcamp demos on a MacBook:
setup scripts, prepared demos, and a printable checklist.

For the full step-by-step setup of a new machine,
print [CHECKLIST.pdf](CHECKLIST.pdf) and tick it off.
This README is the short version.

## What is here

| Path | What it is |
| ---- | ---------- |
| `CHECKLIST.pdf` | Printable provisioning and demo checklist. Built from `CHECKLIST.html` by `scripts/build-checklist.sh`. |
| `scripts/provision.sh` | One-time machine setup, run as the administrator. |
| `scripts/setup-user.sh` | One-time setup of the `demo` user. |
| `scripts/reset.sh` | Return the repository to a clean state after a demo. |
| `Desktop/` | Shown on the `demo` user's Desktop as **Senzing Demos**. |
| `Desktop/Claude`, `ChatGPT`, `Codex`, `GitHub Copilot`, `Kiro` | Links that open the AI apps in `/Applications`. |
| `Desktop/demos/bootcamp/` | Prepared bootcamps, ready to resume. |
| `Desktop/data/` | Source data: CORD samples and truth sets. |
| `Desktop/docs/` | Web pages produced during demos. |

The prepared bootcamps:

| Folder | Business problem |
| ------ | ---------------- |
| `bootcamp-truthset-banking-kyc-kyb-aml` | Banking: KYC, KYB and AML |
| `bootcamp-truthset-pc-insurance-claims-fraud` | P&C insurance: claims fraud |
| `mock-customer-360` | Retail: customer 360 and deduplication |

## Accounts

- `senzing`: administrator. Used only to install software.
- `demo`: standard user. All sign-ins and all demos happen here.

Run demos as `demo`, never as `senzing`.
If a demo needs software that is not installed,
install it as `senzing` without logging out, then add it to `scripts/provision.sh`:

```bash
su - senzing -c 'brew install <package>'
```

## Set up a new Mac (once)

1. As `senzing`, in Terminal:

    ```bash
    curl -fsSLO https://raw.githubusercontent.com/docktermj/senzing-demos-for-mac/main/scripts/provision.sh
    bash provision.sh                          # keep the Mac's current name
    bash provision.sh --name Senzing-demo-01   # or: also rename the Mac
    ```

    It installs Homebrew, git, Python, Java, Maven, the Senzing SDK
    (after you accept the EULA) and the AI apps it can,
    lists any app to install by hand, and creates the `demo` user.
    It is safe to re-run.

2. Log in as `demo`, then in Terminal:

    ```bash
    git clone https://github.com/docktermj/senzing-demos-for-mac.git ~/senzing-demos-for-mac
    bash ~/senzing-demos-for-mac/scripts/setup-user.sh
    ```

3. As `demo`, sign in to each AI app and install the Senzing Bootcamp plugin.
   See section E of [CHECKLIST.pdf](CHECKLIST.pdf),
   or the install pages at <https://hub.senzing.com/bootcamp/>.

## Prepare for a demonstration

As `demo`, before each event:

1. Reset the repository, so the demos match GitHub:

    ```bash
    bash ~/senzing-demos-for-mac/scripts/reset.sh
    ```

2. Check that nothing updated since the last test.
   Compare each AI app's version and the plugin version
   with the ones written in section E of the checklist.
   If anything changed, rehearse one demo before the event.
3. Open the demo you will present:
    1. On the Desktop, open **Senzing Demos** › `demos` › `bootcamp` › the demo's folder.
    2. Open `OPEN_ME_FIRST.md`, then `docs/bootcamp_recap.pdf` for the guided tour.
    3. In the AI app, open the demo's folder and follow `docs/REVISIT_BOOTCAMP.md`
       to restore its database and state.
4. Make the Mac presentation-ready:
    plug in the charger, turn on Do Not Disturb, quit apps you will not show,
    and join the venue's Wi-Fi.

## Reset after a demonstration

As `demo`:

1. Quit the AI apps.
2. Run:

    ```bash
    bash ~/senzing-demos-for-mac/scripts/reset.sh      # shows what it will delete, then asks
    bash ~/senzing-demos-for-mac/scripts/reset.sh -y   # no question
    ```

`reset.sh` fetches the latest from GitHub and makes `~/senzing-demos-for-mac`
exactly match `origin/main`.
It discards every local change, new file and ignored file,
such as databases and build output.
It does not clear the AI apps' chat history or projects; clear those in each app if needed.
If GitHub cannot be reached, it resets to the last copy it fetched.
