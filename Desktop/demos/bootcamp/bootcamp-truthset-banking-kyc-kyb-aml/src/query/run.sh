#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/../.." && pwd)"
cd "$project_root"
source "$project_root/src/scripts/senzing-env.sh"

if [ "$#" -lt 1 ]; then
  printf 'Usage: %s <main-class> [args...]\n' "$0" >&2
  exit 64
fi

json_jar="$project_root/src/server/target/dependency/javax.json-1.1.4.jar"
if [ ! -f "$json_jar" ]; then
  printf 'Missing JSON-P dependency: %s\n' "$json_jar" >&2
  exit 1
fi

main_class=$1
shift
build_dir="$project_root/src/query/.build"
mkdir -p "$build_dir"
javac \
  -cp "$SENZING_ROOT/sdk/java/sz-sdk.jar:$json_jar" \
  -d "$build_dir" \
  "$project_root"/src/query/*.java

exec "$project_root/src/scripts/senzing-java.sh" \
  "$build_dir:$json_jar" "$main_class" "$@"
