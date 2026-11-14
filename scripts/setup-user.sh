#!/usr/bin/env bash
#
# setup-user.sh - Set up the "demo" user's Desktop for Senzing Bootcamp demos.
#
# Run as the "demo" user, after cloning the repository:
#
#   git clone https://github.com/docktermj/senzing-demos-for-mac.git ~/senzing-demos-for-mac
#   bash ~/senzing-demos-for-mac/scripts/setup-user.sh
#
# What it does (safe to re-run):
#   1. Makes ~/Desktop/Senzing Demos a link to ~/senzing-demos-for-mac/Desktop.
#   2. Checks that the app links in that folder (Claude, ChatGPT, ...) are real
#      symbolic links to /Applications, and repairs them if not.
#   3. Adds the Senzing environment to ~/.zshrc.
#   4. Allows local web servers in the Codex config.
#   5. Downloads every released CORD into Desktop/data/cords/<City>/, including
#      the files too large to store in GitHub, and verifies each SHA-256.
#   6. Checks that the tools the bootcamp needs are on PATH.

set -euo pipefail

REPO="$HOME/senzing-demos-for-mac"
DESKTOP_LINK="$HOME/Desktop/Senzing Demos"

info() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

[[ "$(uname -s)" == "Darwin" ]] || die "This script runs on macOS only."
[[ -d "$REPO/.git" ]] || die "Clone the repository to $REPO first."

# ---------------------------------------------------------------------------
# 1. Desktop link
# ---------------------------------------------------------------------------

info "Linking '$DESKTOP_LINK' to $REPO/Desktop"
if [[ -e "$DESKTOP_LINK" && ! -L "$DESKTOP_LINK" ]]; then
    die "'$DESKTOP_LINK' exists and is not a link. Move it away and re-run."
fi
ln -sfn "$REPO/Desktop" "$DESKTOP_LINK"

# ---------------------------------------------------------------------------
# 2. App links
# ---------------------------------------------------------------------------
#
# The repository stores these as git symbolic links, so a normal clone
# creates them. This step repairs them if git checked them out as plain files.
# Each line: <link name in Desktop/>|<app in /Applications>

APP_LINKS="
ChatGPT|ChatGPT.app
Claude|Claude.app
GitHub Copilot|GitHub Copilot.app
Kiro|Kiro.app
"

info "Checking app links"
while IFS='|' read -r name app; do
    [[ -n "$name" ]] || continue
    link="$REPO/Desktop/$name"
    target="/Applications/$app"
    if [[ "$(readlink "$link" 2>/dev/null || true)" != "$target" ]]; then
        rm -f "$link"
        ln -s "$target" "$link"
        echo "  $name: repaired"
    else
        echo "  $name: ok"
    fi
    [[ -d "$target" ]] || warn "$target is not installed; '$name' will not open."
done <<< "$APP_LINKS"

# ---------------------------------------------------------------------------
# 3. Senzing environment in ~/.zshrc (Java needs DYLD_LIBRARY_PATH at launch)
# ---------------------------------------------------------------------------

MARKER="# >>> senzing-demos-for-mac >>>"
if ! grep -qF "$MARKER" "$HOME/.zshrc" 2>/dev/null; then
    info "Adding the Senzing environment to ~/.zshrc"
    cat >> "$HOME/.zshrc" <<'EOF'
# >>> senzing-demos-for-mac >>>
if [[ -d "$(brew --prefix)/opt/senzing/er" ]]; then
    export SENZING_ROOT="$(brew --prefix)/opt/senzing/er"
    export DYLD_LIBRARY_PATH="${SENZING_ROOT}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}"
    export PATH="${SENZING_ROOT}/bin:${PATH}"
fi
# <<< senzing-demos-for-mac <<<
EOF
fi

# ---------------------------------------------------------------------------
# 4. Codex config: the ChatGPT bootcamp runs local web servers
# ---------------------------------------------------------------------------

CODEX_CONFIG="$HOME/.codex/config.toml"
if ! grep -qF "[experimental_network]" "$CODEX_CONFIG" 2>/dev/null; then
    info "Allowing local web servers in $CODEX_CONFIG"
    mkdir -p "$HOME/.codex"
    printf '\n[experimental_network]\nenabled = true\nallow_local_binding = true\n' >> "$CODEX_CONFIG"
fi

# ---------------------------------------------------------------------------
# 5. CORDs: download every released file and verify its SHA-256
# ---------------------------------------------------------------------------
#
# Some CORDs (equifax-lasvegas.jsonl, opendata-lasvegas.jsonl) are too large
# to store in GitHub, so fetch them all from the CORD catalog, as recommended
# in https://github.com/Senzing/cords/blob/main/development/bulk-download.md
# A file whose SHA-256 already matches is kept, so a re-run downloads only
# what is missing or changed.

CORDS_DIR="$REPO/Desktop/data/cords"
CORDS_BASE="https://hub.senzing.com/cords"

# Map a CORD file name (e.g. icij-lasvegas.jsonl) to its city directory.
cord_city() {
    local suffix="${1##*-}"
    case "$suffix" in
        lasvegas*) echo "LasVegas" ;;
        london*)   echo "London" ;;
        moscow*)   echo "Moscow" ;;
        *)         echo "Other" ;;
    esac
}

info "Downloading CORDs into $CORDS_DIR"
cords_tmp="$(mktemp -d)"
trap 'rm -rf "$cords_tmp"' EXIT
curl -fsSL -o "$cords_tmp/SHA256SUMS" "$CORDS_BASE/SHA256SUMS" \
    && curl -fsSL -o "$cords_tmp/urls.txt" "$CORDS_BASE/urls.txt" \
    || die "Could not download the CORD lists from $CORDS_BASE."

cords_failed=0
while read -r url; do
    [[ -n "$url" ]] || continue
    name="${url##*/}"
    expected="$(awk -v n="$name" '$2 == n { print $1 }' "$cords_tmp/SHA256SUMS")"
    if [[ -z "$expected" ]]; then
        warn "$name has no SHA-256 in SHA256SUMS; skipping."
        cords_failed=1
        continue
    fi
    dir="$CORDS_DIR/$(cord_city "$name")"
    path="$dir/$name"
    mkdir -p "$dir"

    # Keep a file whose SHA-256 already matches; download anything else.
    if [[ -f "$path" && "$(shasum -a 256 "$path" | cut -d' ' -f1)" == "$expected" ]]; then
        echo "  $name: ok"
        continue
    fi
    if ! curl -fsSL -o "$cords_tmp/$name" "$url"; then
        warn "Download failed: $url"
        cords_failed=1
        continue
    fi
    if [[ "$(shasum -a 256 "$cords_tmp/$name" | cut -d' ' -f1)" != "$expected" ]]; then
        warn "$name: SHA-256 mismatch after download; not installed."
        cords_failed=1
        continue
    fi
    mv "$cords_tmp/$name" "$path"
    echo "  $name: downloaded"
done < "$cords_tmp/urls.txt"

[[ "$cords_failed" -eq 0 ]] || die "Some CORDs are missing or failed verification. Re-run to retry."

# ---------------------------------------------------------------------------
# 6. Tools
# ---------------------------------------------------------------------------

info "Checking tools"
for tool in git gh python3 java mvn jq; do
    if command -v "$tool" >/dev/null 2>&1; then
        echo "  $tool: $(command -v "$tool")"
    else
        warn "$tool is not on PATH. Open a new Terminal window, or re-run provision.sh as the admin user."
    fi
done

cat <<EOF

Done. Next, as this user:
  - Sign in to Claude, ChatGPT/Codex, GitHub Copilot and Kiro.
  - Install the Senzing Bootcamp plugin in each app (see https://hub.senzing.com/bootcamp/).
EOF
