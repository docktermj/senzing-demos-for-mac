# Project-local Senzing environment. Source it; do not execute it:
#   source src/scripts/senzing-env.sh
#
# Values from sdk_guide(topic='install', platform='linux_apt', language='python'),
# Python gotchas[] entry: set BOTH PYTHONPATH and LD_LIBRARY_PATH.

# --- resolve this script's own location (bash and zsh) ------------------------
# ${BASH_SOURCE[0]} is bash-only and expands to EMPTY under zsh, so branch.
if [ -n "${ZSH_VERSION:-}" ]; then
  _sz_self=${(%):-%x}
else
  _sz_self=${BASH_SOURCE[0]:-$0}
fi
_sz_root=$(cd -- "$(dirname -- "$_sz_self")/../.." && pwd)

# --- fail loudly, naming the path that was computed --------------------------
if [ ! -f "$_sz_root/config/bootcamp_progress.json" ]; then
  printf 'senzing-env.sh: resolved project root has no config/bootcamp_progress.json\n' >&2
  printf 'senzing-env.sh:   resolved root: %s\n' "$_sz_root" >&2
  printf 'senzing-env.sh:   this is a path-resolution fault, not your Senzing install\n' >&2
  unset _sz_self _sz_root
  return 1 2>/dev/null || exit 1
fi

# --- engine configuration: skip while absent, never export an empty one -----
if [ ! -f "$_sz_root/config/engine_config.json" ]; then
  printf 'senzing-env.sh: config/engine_config.json not written yet, so SENZING_ENGINE_CONFIGURATION_JSON is not set; source this script again after Step 8\n' >&2
elif _sz_settings=$(cat -- "$_sz_root/config/engine_config.json"); [ -z "$_sz_settings" ]; then
  printf 'senzing-env.sh: %s is empty — refusing to export an empty configuration\n' \
    "$_sz_root/config/engine_config.json" >&2
  unset _sz_self _sz_root _sz_settings
  return 1 2>/dev/null || exit 1
else
  export SENZING_ENGINE_CONFIGURATION_JSON="$_sz_settings"
fi

export SENZING_PROJECT_ROOT="$_sz_root"
export PYTHONPATH="/opt/senzing/er/sdk/python${PYTHONPATH:+:$PYTHONPATH}"
export LD_LIBRARY_PATH="/opt/senzing/er/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

unset _sz_self _sz_root _sz_settings
