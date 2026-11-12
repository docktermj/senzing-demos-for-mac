#!/usr/bin/env bash
set -euo pipefail

if [ -n "${ZSH_VERSION:-}" ]; then
  _sz_self=${(%):-%x}
else
  _sz_self=${BASH_SOURCE[0]:-$0}
fi
_sz_root=$(cd -- "$(dirname -- "$_sz_self")/../.." && pwd)

if [ -z "${SENZING_ENGINE_CONFIGURATION_JSON:-}" ]; then
  if [ -f "$_sz_root/config/engine_config.json" ]; then
    SENZING_ENGINE_CONFIGURATION_JSON=$(cat -- "$_sz_root/config/engine_config.json")
    export SENZING_ENGINE_CONFIGURATION_JSON
  else
    printf 'senzing-env.sh: set SENZING_ENGINE_CONFIGURATION_JSON or provide config/engine_config.json\n' >&2
    unset _sz_self _sz_root
    return 1 2>/dev/null || exit 1
  fi
fi

if [ -z "${SENZING_ROOT:-}" ]; then
  if ! command -v brew >/dev/null 2>&1; then
    printf 'senzing-env.sh: set SENZING_ROOT to the SDK installation directory\n' >&2
    unset _sz_self _sz_root
    return 1 2>/dev/null || exit 1
  fi
  _sz_brew_prefix=$(brew --prefix)
  SENZING_ROOT="$_sz_brew_prefix/opt/senzing/er"
fi

if [ ! -f "$SENZING_ROOT/sdk/java/sz-sdk.jar" ]; then
  printf 'senzing-env.sh: Java SDK archive not found under %s\n' "$SENZING_ROOT" >&2
  unset _sz_self _sz_root _sz_brew_prefix
  return 1 2>/dev/null || exit 1
fi

export SENZING_ROOT
export PATH="$SENZING_ROOT/bin:$PATH"
if [ "$(uname -s)" = "Darwin" ]; then
  export DYLD_LIBRARY_PATH="$SENZING_ROOT/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
fi

unset _sz_self _sz_root _sz_brew_prefix
