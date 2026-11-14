# senzing-env.sh — set up this project's Senzing environment in the CURRENT shell.
#
#   source src/scripts/senzing-env.sh        (bash or zsh; must be sourced, not executed)
#
# Exports SENZING_PROJECT_ROOT, SENZING_ROOT, SENZING_SDK_JAR, the native library path, and
# SENZING_ENGINE_CONFIGURATION_JSON (kept if already set, otherwise read from
# config/engine_config.json). See config/engine_config.example.json for the shape.

if [ -n "${ZSH_VERSION:-}" ]; then
  _sz_self=${(%):-%x}
else
  _sz_self=${BASH_SOURCE[0]:-$0}
fi
_sz_root=$(cd -- "$(dirname -- "$_sz_self")/../.." && pwd)
if [ ! -f "$_sz_root/config/data_sources.yaml" ]; then
  printf 'senzing-env.sh: %s does not look like the project root (no config/data_sources.yaml)\n' "$_sz_root" >&2
  unset _sz_self _sz_root
  return 1 2>/dev/null || exit 1
fi
export SENZING_PROJECT_ROOT="$_sz_root"

if [ -z "${SENZING_ENGINE_CONFIGURATION_JSON:-}" ]; then
  if [ -s "$_sz_root/config/engine_config.json" ]; then
    export SENZING_ENGINE_CONFIGURATION_JSON="$(cat -- "$_sz_root/config/engine_config.json")"
  else
    printf 'senzing-env.sh: SENZING_ENGINE_CONFIGURATION_JSON is not set and config/engine_config.json is missing;\n' >&2
    printf 'senzing-env.sh: copy config/engine_config.example.json to config/engine_config.json and edit it\n' >&2
  fi
fi

# Senzing install location: Homebrew on macOS, /opt/senzing/er on Linux.
if [ -z "${SENZING_ROOT:-}" ]; then
  if command -v brew >/dev/null 2>&1 && [ -d "$(brew --prefix)/opt/senzing/er" ]; then
    SENZING_ROOT="$(brew --prefix)/opt/senzing/er"
  else
    SENZING_ROOT="/opt/senzing/er"
  fi
fi
export SENZING_ROOT
case "$(uname -s)" in
  Darwin) export DYLD_LIBRARY_PATH="${SENZING_ROOT}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}" ;;
  *)      export LD_LIBRARY_PATH="${SENZING_ROOT}/lib${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}" ;;
esac
export PATH="${SENZING_ROOT}/bin:${PATH}"
export SENZING_SDK_JAR="${SENZING_ROOT}/sdk/java/sz-sdk.jar"
[ -f "$SENZING_SDK_JAR" ] || printf 'senzing-env.sh: no sz-sdk.jar under %s\n' "$SENZING_ROOT" >&2

unset _sz_self _sz_root
