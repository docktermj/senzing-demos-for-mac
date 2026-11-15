# Packing list

## Users

1. Demonstration user
    1. Username: `demo`
    1. Password: `demo`
1. Administration user
    1. Username: `senzing`
    1. Password:

Log in as `demo` for everything below.
Use `senzing` only to install software.

## Demonstrations

1. The data, documents and scripts for the demonstrations are in the **Senzing Demos** folder on the Desktop.
1. The AI assistant sessions for the demonstrations are in the `~/demos` directory
   (`/Users/demo/demos`).
   This directory was created by hand on this Mac.
   It is not part of the GitHub repository,
   so updating, resetting or replacing the **Senzing Demos** folder does not change it.
1. Copilot
    1. Launch the **GitHub Copilot** icon.
    1. Open the demonstration's session from the app's list of recent sessions.
       Each session works in a folder in `~/demos`.
       The sessions are:
        1. Banking bootcamp and recipe
        1. Insurance bootcamp and recipe
1. Kiro
    1. The icons to launch Kiro are in the `Kiro-scripts` folder.
       Double-click one to run it.
       A Terminal window opens, then Kiro opens the demonstration.
       The icons are:
        1. `banking-bootcamp`
        1. `banking-recipe`
        1. `insurance-bootcamp`
        1. `insurance-recipe`
1. Claude
    1. Launch the **Claude** icon.
    1. Open the demonstration's session from the app's list of recent sessions.
       Each session works in a folder in `~/demos`.
       The sessions are:
        1. Banking bootcamp and recipe
        1. Insurance bootcamp and recipe

## Resetting the Senzing Demos folder

After a demonstration, return the **Senzing Demos** folder to what is on GitHub.
This discards every change made in that folder during the demonstration.
It does not change the AI assistant sessions in `~/demos`.

1. Quit the AI apps.
1. Open a Terminal.
1. In the terminal, run:

    ```console
    bash ~/senzing-demos-for-mac/scripts/reset.sh
    ```

    It lists what it will discard, then asks before changing anything.

## Updating the Senzing Demos folder

To update the contents of the **Senzing Demos** folder:

1. Open a Terminal.
1. In the terminal, run:

    ```console
    cd ~/senzing-demos-for-mac
    git pull
    ```

    If `git pull` fails because of local changes,
    run the steps in [Resetting the Senzing Demos folder](#resetting-the-senzing-demos-folder) instead.
    That also fetches the latest from GitHub.

## Replacing the Senzing Demos folder

To replace the contents of the **Senzing Demos** folder:

1. Open a Terminal.
1. In the terminal, run:

    ```console
    mv ~/senzing-demos-for-mac ~/senzing-demos-for-mac-$(date +%s)
    cd ~
    git clone https://github.com/docktermj/senzing-demos-for-mac.git
    bash ~/senzing-demos-for-mac/scripts/setup-user.sh
    ```
