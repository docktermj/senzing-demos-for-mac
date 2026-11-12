#!/usr/bin/env bash
#
# provision.sh - Provision a factory-reset MacBook for Senzing Bootcamp demos.
#
# Run ONCE, as the admin user ("senzing"), from Terminal:
#
#   curl -fsSLO https://raw.githubusercontent.com/docktermj/senzing-demos-for-mac/main/scripts/provision.sh
#   bash provision.sh 01        # or 02
#   bash provision.sh --no-rename   # test run: keep the Mac's current name
#
# What it does (each step is skipped if already done, so re-running is safe):
#   1. Names the machine "Senzing-demo-NN".
#   2. Installs Homebrew (which also installs the Xcode Command Line Tools).
#   3. Puts Homebrew on every user's PATH, including the standard "demo" user.
#   4. Installs command-line tools: git, gh, python, jq, Java and Maven,
#      and the Senzing SDK (after you accept its EULA).
#   5. Installs the AI apps it can through Homebrew, and lists the ones you
#      must install by hand.
#   6. Keeps the Mac awake while on power.
#   7. Creates the standard "demo" user if it does not exist.
#
# Written for the macOS default /bin/bash (3.2): no associative arrays.

set -euo pipefail

DEMO_USER="demo"
MANUAL_INSTALLS=""

info() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# Preconditions
# ---------------------------------------------------------------------------

[[ "$(uname -s)" == "Darwin" ]] || die "This script runs on macOS only."
[[ "$EUID" -ne 0 ]] || die "Run as the admin user, not with sudo. The script asks for sudo when it needs it."
id -Gn | grep -qw admin || die "User '$(whoami)' is not an administrator."

USAGE="Usage: bash provision.sh NN            (for example: bash provision.sh 01)
       bash provision.sh --no-rename   (keep this Mac's name; for testing on a non-demo Mac)"

case "${1:-}" in
    --no-rename)
        MACHINE_NAME=""
        ;;
    [0-9][0-9])
        MACHINE_NAME="Senzing-demo-$1"
        ;;
    *)
        die "$USAGE"
        ;;
esac

info "Asking for your password once, for the steps that need sudo"
sudo -v
# Keep sudo alive until this script exits.
while true; do sudo -n true; sleep 50; kill -0 "$$" 2>/dev/null || exit; done 2>/dev/null &

# ---------------------------------------------------------------------------
# 1. Machine name
# ---------------------------------------------------------------------------

if [[ -z "$MACHINE_NAME" ]]; then
    MACHINE_NAME="$(scutil --get ComputerName)"
    info "Keeping this Mac's name: ${MACHINE_NAME} (--no-rename)"
else
    info "Naming this Mac ${MACHINE_NAME}"
    sudo scutil --set ComputerName  "$MACHINE_NAME"
    sudo scutil --set HostName      "$MACHINE_NAME"
    sudo scutil --set LocalHostName "$MACHINE_NAME"
    sudo defaults write /Library/Preferences/SystemConfiguration/com.apple.smb.server NetBIOSName -string "$MACHINE_NAME"
fi

# ---------------------------------------------------------------------------
# 2. Homebrew (installs the Xcode Command Line Tools, so it comes before git)
# ---------------------------------------------------------------------------

BREW=/opt/homebrew/bin/brew
[[ "$(uname -m)" == "arm64" ]] || BREW=/usr/local/bin/brew

if [[ -x "$BREW" ]]; then
    info "Homebrew is already installed"
else
    info "Installing Homebrew (this also installs the Xcode Command Line Tools; it takes a while)"
    NONINTERACTIVE=1 /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
fi
eval "$("$BREW" shellenv)"

# ---------------------------------------------------------------------------
# 3. Homebrew on PATH for every user
# ---------------------------------------------------------------------------

info "Putting Homebrew on every user's PATH"
BREW_PREFIX="$(brew --prefix)"
printf '%s/bin\n%s/sbin\n' "$BREW_PREFIX" "$BREW_PREFIX" | sudo tee /etc/paths.d/homebrew >/dev/null

# ---------------------------------------------------------------------------
# 4. Command-line tools
# ---------------------------------------------------------------------------

info "Updating Homebrew"
brew update

# python : the bootcamp hooks and recap need python3 on PATH.
# openjdk, maven : the canned Copilot demo is written in Java (pom.xml).
FORMULAE="git gh python jq openjdk@21 maven"
for f in $FORMULAE; do
    if brew list --formula "$f" >/dev/null 2>&1; then
        echo "  $f: already installed"
    else
        info "Installing $f"
        brew install "$f"
    fi
done

# Let macOS's /usr/bin/java find the Homebrew JDK.
JDK_LINK=/Library/Java/JavaVirtualMachines/openjdk-21.jdk
if [[ ! -e "$JDK_LINK" ]]; then
    sudo ln -sfn "$(brew --prefix openjdk@21)/libexec/openjdk.jdk" "$JDK_LINK"
fi

# ---------------------------------------------------------------------------
# 4b. Senzing SDK (Apple Silicon only; steps from the Senzing MCP sdk_guide)
# ---------------------------------------------------------------------------

SZ_LIB="$(brew --prefix)/opt/senzing/er/lib/libSz.dylib"
if [[ "$(uname -m)" != "arm64" ]]; then
    warn "The Senzing SDK Homebrew cask is Apple Silicon only. Skipping the SDK on this Intel Mac."
elif [[ -f "$SZ_LIB" ]]; then
    info "Senzing SDK is already installed"
else
    info "Installing the Senzing SDK"
    echo "Installing Senzing requires accepting the Senzing End User License Agreement:"
    echo "  https://senzing.com/end-user-license-agreement/"
    printf 'Do you accept the EULA? [y/N] '
    read -r answer < /dev/tty
    if [[ "$answer" =~ ^[Yy]$ ]]; then
        brew tap senzing/senzingsdk https://github.com/Senzing/homebrew-senzingsdk
        brew trust senzing/senzingsdk 2>/dev/null || true   # Homebrew 6+ only
        HOMEBREW_SENZING_ACCEPT_EULA=i_accept_the_senzing_eula brew install --cask senzingsdk
        # A zero exit code does not prove the install happened; check the files.
        if [[ -f "$SZ_LIB" ]] && ls "$(brew --prefix)/opt/senzing/data"/*TransRules.sz >/dev/null 2>&1; then
            echo "  Senzing SDK: installed"
        else
            warn "Senzing SDK did NOT install (libSz.dylib or support data missing)."
            MANUAL_INSTALLS="${MANUAL_INSTALLS}  - Senzing SDK: re-run provision.sh and check the brew output"$'\n'
        fi
    else
        warn "EULA not accepted. Skipping the Senzing SDK."
        MANUAL_INSTALLS="${MANUAL_INSTALLS}  - Senzing SDK: EULA not accepted"$'\n'
    fi
fi

# ---------------------------------------------------------------------------
# 5. Applications
# ---------------------------------------------------------------------------
#
# Each line: <cask name>|<app in /Applications>|<where to get it by hand>
# A cask name of "-" means there is no Homebrew cask: install by hand.

APPS="
claude|Claude.app|https://claude.ai/download
chatgpt|ChatGPT.app|https://chatgpt.com/download
-|Codex.app|https://chatgpt.com/codex
kiro|Kiro.app|https://kiro.dev/downloads
-|GitHub Copilot.app|https://hub.dockter.com/senzing-bootcamp-copilot-plugin-development/site/
copilot-cli|-|https://github.com/github/copilot-cli
"

install_app() {
    local cask="$1" app="$2" url="$3"
    if [[ "$app" != "-" && -d "/Applications/$app" ]]; then
        echo "  $app: already installed"
        return
    fi
    if [[ "$cask" != "-" ]] && brew info --cask "$cask" >/dev/null 2>&1; then
        info "Installing $cask"
        if brew install --cask "$cask"; then
            return
        fi
        warn "brew install --cask $cask failed"
    fi
    MANUAL_INSTALLS="${MANUAL_INSTALLS}  - ${app/#-/$cask}: ${url}"$'\n'
}

info "Installing applications"
while IFS='|' read -r cask app url; do
    [[ -n "$cask" ]] || continue
    install_app "$cask" "$app" "$url"
done <<< "$APPS"

# ---------------------------------------------------------------------------
# 6. Power: stay awake while plugged in
# ---------------------------------------------------------------------------

info "Keeping the Mac awake while plugged in"
sudo pmset -c sleep 0 displaysleep 0 disksleep 0

# ---------------------------------------------------------------------------
# 7. The standard "demo" user
# ---------------------------------------------------------------------------

if id "$DEMO_USER" >/dev/null 2>&1; then
    info "User '$DEMO_USER' already exists"
else
    info "Creating standard user '$DEMO_USER'. Enter the password for '$DEMO_USER' when asked."
    sudo sysadminctl -addUser "$DEMO_USER" -fullName "Senzing Demo" -password -
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

info "Done provisioning ${MACHINE_NAME}"
echo "  git:     $(git --version)"
echo "  python3: $(python3 --version)"
echo "  java:    $(java -version 2>&1 | head -1)"

if [[ -n "$MANUAL_INSTALLS" ]]; then
    echo
    warn "Install these by hand (drag each app to /Applications):"
    printf '%s' "$MANUAL_INSTALLS"
fi

cat <<EOF

Next:
  1. Install anything listed above, then run Software Update and restart.
  2. Log in as '${DEMO_USER}' and, in Terminal, run:
       git clone https://github.com/docktermj/senzing-demos-for-mac.git ~/senzing-demos-for-mac
       bash ~/senzing-demos-for-mac/scripts/setup-user.sh
EOF
