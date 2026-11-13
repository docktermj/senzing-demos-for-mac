#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/../.." && pwd)"
cd "$project_root"
source "$project_root/src/scripts/senzing-env.sh"

registry="$project_root/config/data_sources.yaml"
json_jar="$project_root/src/server/target/dependency/javax.json-1.1.4.jar"
if [ ! -f "$registry" ]; then
  printf 'Missing source registry: %s\n' "$registry" >&2
  exit 1
fi
if [ ! -f "$json_jar" ]; then
  printf 'Missing JSON-P dependency: %s\n' "$json_jar" >&2
  exit 1
fi

mkdir -p "$project_root/data/temp" "$project_root/logs"
runtime_root="$(mktemp -d "$project_root/data/temp/.load-runtime.XXXXXX")"
cleanup() {
  rm -rf "$runtime_root"
}
trap cleanup EXIT

mkdir -p "$runtime_root/config"
python3 - "$registry" "$runtime_root/config/data_sources.yaml" <<'PY'
import re
import sys
from pathlib import Path

source = Path(sys.argv[1])
destination = Path(sys.argv[2])
lines = source.read_text(encoding="utf-8").splitlines()
output = []
in_sources = False
found = 0
for line in lines:
    if not line.strip() or line.lstrip().startswith("#"):
        output.append(line)
        continue
    if not line.startswith(" "):
        in_sources = line.strip() == "sources:"
    if in_sources and re.fullmatch(r"  [A-Z][A-Z0-9_]*:\s*", line):
        output.extend((line, "    mapping_status: complete", "    load_status: not_loaded"))
        found += 1
    else:
        output.append(line)
if not found:
    raise SystemExit("No source entries found in the registry.")
destination.write_text("\n".join(output) + "\n", encoding="utf-8")
PY

ln -s "$project_root/src" "$runtime_root/src"
ln -s "$project_root/data" "$runtime_root/data"
ln -s "$project_root/database" "$runtime_root/database"
ln -s "$project_root/logs" "$runtime_root/logs"

(
  cd "$runtime_root"
  java_classpath="src/load/.build:$json_jar"
  "$project_root/src/scripts/senzing-java.sh" \
    "$java_classpath" loading.LoadOrchestrator "$@"
)
