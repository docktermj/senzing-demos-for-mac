#!/usr/bin/env bash
#
# reset.sh - Return ~/senzing-demos-for-mac to exactly what is on GitHub.
#
# Run as the "demo" user after a demo:
#
#   bash ~/senzing-demos-for-mac/scripts/reset.sh        # shows what it will delete, then asks
#   bash ~/senzing-demos-for-mac/scripts/reset.sh -y     # no question
#
# It discards every local change in the repository: edited files, new files,
# and ignored files such as databases and build output. It does not touch
# anything outside the repository, such as the AI apps' chat history.

set -euo pipefail

REPO="$HOME/senzing-demos-for-mac"
BRANCH="main"
ASSUME_YES=0
[[ "${1:-}" == "-y" ]] && ASSUME_YES=1

info() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

[[ -d "$REPO/.git" ]] || die "No repository at $REPO."
cd "$REPO"

info "Fetching the latest from GitHub"
if ! git fetch origin "$BRANCH"; then
    echo "Could not reach GitHub; resetting to the last fetched copy instead."
fi

info "These changes will be discarded"
git status --short
git clean -ndx

if [[ "$ASSUME_YES" -ne 1 ]]; then
    printf '\nReset %s to origin/%s? [y/N] ' "$REPO" "$BRANCH"
    read -r answer
    [[ "$answer" =~ ^[Yy]$ ]] || { echo "Nothing changed."; exit 0; }
fi

info "Resetting"
git checkout -q "$BRANCH"
git reset -q --hard "origin/$BRANCH"
git clean -qfdx

info "Done. $REPO matches origin/$BRANCH at $(git rev-parse --short HEAD)."
