#!/usr/bin/env bash
set -euo pipefail

_sz_script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
_sz_settings_override=${SENZING_ENGINE_CONFIGURATION_JSON_OVERRIDE-}
source "${_sz_script_dir}/senzing-env.sh"

if [[ -n "$_sz_settings_override" ]]; then
  export SENZING_ENGINE_CONFIGURATION_JSON="$_sz_settings_override"
fi
unset _sz_settings_override

if [[ $# -eq 0 ]]; then
  printf 'Usage: %s <main-class> [args...]\n' "$0" >&2
  exit 2
fi

_sz_jar="${SENZING_ROOT}/sdk/java/sz-sdk.jar"
if [[ ! -f "$_sz_jar" ]]; then
  printf 'run-java.sh: Senzing Java SDK jar not found at %s\n' "$_sz_jar" >&2
  exit 1
fi
_sz_jar_dir=$(cd -- "$(dirname -- "$_sz_jar")" && pwd -P)
_sz_jar="${_sz_jar_dir}/$(basename "$_sz_jar")"
printf 'Senzing Java SDK jar: %s\n' "$_sz_jar" >&2

if [[ -n "${JAVA_HOME:-}" ]]; then
  _sz_java="${JAVA_HOME%/}/bin/java"
else
  _sz_java_home=$(/usr/libexec/java_home)
  _sz_java="${_sz_java_home}/bin/java"
fi

if [[ ! -x "$_sz_java" ]]; then
  printf 'run-java.sh: Java executable not found at %s\n' "$_sz_java" >&2
  exit 1
fi

exec "$_sz_java" --enable-native-access=ALL-UNNAMED \
  -Djava.library.path="${SENZING_ROOT}/lib" \
  -cp "${_sz_jar}:${SENZING_PROJECT_ROOT}/target/classes:${SENZING_PROJECT_ROOT}/target/dependency/*" "$@"
