# Senzing environment for this project (macOS Apple Silicon, Java, Homebrew Senzing SDK).
# Source it, do not execute it:   source src/scripts/senzing-env.sh
#
# Exports SENZING_ROOT, DYLD_LIBRARY_PATH, PATH, SENZING_SDK_JAR, SENZING_JAVA_HOME and
# SENZING_PROJECT_ROOT. SENZING_ENGINE_CONFIGURATION_JSON is taken from the environment when
# already set (e.g. from .env or a secret manager), otherwise from config/engine_config.json
# (not committed; see .env.example for its shape). It is never exported empty.
# On Linux, replace the DYLD_LIBRARY_PATH line with LD_LIBRARY_PATH=/opt/senzing/er/lib and
# SENZING_ROOT=/opt/senzing/er.

if [ -n "${ZSH_VERSION:-}" ]; then
  _sz_self=${(%):-%x}
else
  _sz_self=${BASH_SOURCE[0]:-$0}
fi
_sz_root=$(cd -- "$(dirname -- "$_sz_self")/../.." && pwd)

if [ ! -f "$_sz_root/config/data_sources.yaml" ]; then
  printf 'senzing-env.sh: resolved project root has no config/data_sources.yaml (root: %s)\n' "$_sz_root" >&2
  unset _sz_self _sz_root
  return 1 2>/dev/null || exit 1
fi

if [ -z "${SENZING_ENGINE_CONFIGURATION_JSON:-}" ]; then
  if [ -s "$_sz_root/config/engine_config.json" ]; then
    export SENZING_ENGINE_CONFIGURATION_JSON="$(cat -- "$_sz_root/config/engine_config.json")"
  else
    printf 'senzing-env.sh: SENZING_ENGINE_CONFIGURATION_JSON is not set and config/engine_config.json is missing or empty\n' >&2
  fi
fi

export SENZING_PROJECT_ROOT="$_sz_root"
_sz_brew=$(brew --prefix)
export SENZING_ROOT="$_sz_brew/opt/senzing/er"
export DYLD_LIBRARY_PATH="${SENZING_ROOT}/lib:$_sz_brew/opt/sqlite/lib:$_sz_brew/opt/openssl@3/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
export PATH="${SENZING_ROOT}/bin:${PATH}"
export SENZING_SDK_JAR="${SENZING_ROOT}/sdk/java/sz-sdk.jar"
# Launch the real JDK, not the /usr/bin/java stub: macOS strips DYLD_* variables when it starts
# a protected /usr/bin program.
export SENZING_JAVA_HOME="$(/usr/libexec/java_home)"
unset _sz_self _sz_root _sz_brew
