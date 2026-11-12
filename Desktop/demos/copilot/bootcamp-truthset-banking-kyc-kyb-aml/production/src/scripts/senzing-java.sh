#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/senzing-env.sh"

if [ "$#" -lt 2 ]; then
  printf 'Usage: %s <application-classpath> <main-class> [args...]\n' "$0" >&2
  exit 64
fi

application_classpath=$1
main_class=$2
shift 2
sdk_classpath="$SENZING_ROOT/sdk/java/sz-sdk.jar"
if [ -n "$application_classpath" ]; then
  sdk_classpath="$sdk_classpath:$application_classpath"
fi

exec java \
  -Djava.library.path="$SENZING_ROOT/lib" \
  -cp "$sdk_classpath" \
  "$main_class" "$@"
