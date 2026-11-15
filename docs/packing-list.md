# Packing list

## Users

1. Demonstration user
    1. Username: `demo`
    1. Password: `demo`
1. Administation user
    1. Username: `senzing`
    1. Password:

## Demonstrations

1. All demonstrations are in the **Senzing Demos** folder on the Desktop
1. Copilot
    1. Launch the **Copilot** icon
    1. In the Copilot app are:
        1. Banking bootcamp and recipe
        1. Insurance bootcamp and recipe
    1. The actual code is in the `/Users/demo/demos` directory.
1. Kiro
    1. The icons to launch Kiro are in the `Kiro-scripts` folder
    1. The actual code is in the `/Users/demo/demos` directory.

## Updating the Senzing Demos folder

To update the contents of the **Senzing Demos** folder:

    1. Open a Terminal
    1. In the terminal, run

    ```console
    cd ~/senzing-demos-for-mac
    git update
    ```

## Replacing the Senzing Demos folder

To replace the contents of the **Senzing Demos** folder:

    1. Open a Terminal
    1. In the terminal, run

    ```console
    mv ~/senzing-demos-for-mac ~/senzing-demos-for-mac-$(date +$s)
    cd ~
    git clone https://github.com/docktermj/senzing-demos-for-mac.git
    bash ~/senzing-demos-for-mac/scripts/setup-user.sh
    ```
