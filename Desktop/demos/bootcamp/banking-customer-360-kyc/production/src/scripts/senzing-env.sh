# senzing-env.sh — project-local Senzing environment for this project (source it; do not execute it).
#   source src/scripts/senzing-env.sh
# Reads .env from the project root when present. If SENZING_ENGINE_CONFIGURATION_JSON is not set,
# builds it from DATABASE_URL (a Senzing connection string) and, when set, SENZING_LICENSE_FILE
# (written into the engine configuration as PIPELINE.LICENSEFILE). Never edits global shell config.
# Platform exports are for macOS Apple Silicon with the Homebrew Senzing install.

if [ -n "${ZSH_VERSION:-}" ]; then _sz_self=${(%):-%x}; else _sz_self=${BASH_SOURCE[0]:-$0}; fi
_sz_root=$(cd -- "$(dirname -- "$_sz_self")/../.." && pwd)
export SENZING_PROJECT_ROOT="$_sz_root"

if [ -f "$_sz_root/.env" ]; then set -a; . "$_sz_root/.env"; set +a; fi

export SENZING_ROOT="$(brew --prefix)/opt/senzing/er"
export DYLD_LIBRARY_PATH="${SENZING_ROOT}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}"
export PATH="${SENZING_ROOT}/bin:${PATH}"
export SZ_SDK_JAR="${SENZING_ROOT}/sdk/java/sz-sdk.jar"
# /usr/bin/java drops DYLD_LIBRARY_PATH on macOS, so launch the JDK binary directly.
export SZ_JAVA="$(/usr/libexec/java_home)/bin/java"

if [ -z "${SENZING_ENGINE_CONFIGURATION_JSON:-}" ]; then
  if [ -z "${DATABASE_URL:-}" ]; then
    printf 'senzing-env.sh: set SENZING_ENGINE_CONFIGURATION_JSON or DATABASE_URL (see .env.example)\n' >&2
    unset _sz_self _sz_root; return 1 2>/dev/null || exit 1
  fi
  _sz_lic=""
  [ -n "${SENZING_LICENSE_FILE:-}" ] && _sz_lic=", \"LICENSEFILE\": \"${SENZING_LICENSE_FILE}\""
  export SENZING_ENGINE_CONFIGURATION_JSON="{\"PIPELINE\": {\"CONFIGPATH\": \"${SENZING_ROOT}/etc\", \"RESOURCEPATH\": \"${SENZING_ROOT}/resources\", \"SUPPORTPATH\": \"$(brew --prefix)/opt/senzing/data\"${_sz_lic}}, \"SQL\": {\"CONNECTION\": \"${DATABASE_URL}\"}}"
fi
unset _sz_self _sz_root _sz_lic
