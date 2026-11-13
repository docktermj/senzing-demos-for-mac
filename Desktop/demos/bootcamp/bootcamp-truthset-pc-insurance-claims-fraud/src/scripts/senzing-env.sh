if [ -n "${ZSH_VERSION:-}" ]; then
  _sz_self=${(%):-%x}
else
  _sz_self=${BASH_SOURCE[0]:-$0}
fi
_sz_root=$(cd -- "$(dirname -- "$_sz_self")/../.." && pwd)

if [ ! -f "$_sz_root/config/bootcamp_progress.json" ]; then
  printf 'senzing-env.sh: resolved project root has no config/bootcamp_progress.json\n' >&2
  printf 'senzing-env.sh:   resolved root: %s\n' "$_sz_root" >&2
  printf 'senzing-env.sh:   this is a path-resolution fault, not your Senzing install\n' >&2
  unset _sz_self _sz_root
  return 1 2>/dev/null || exit 1
fi

if [ ! -f "$_sz_root/config/engine_config.json" ]; then
  unset SENZING_ENGINE_CONFIGURATION_JSON
  printf 'senzing-env.sh: config/engine_config.json not written yet, so SENZING_ENGINE_CONFIGURATION_JSON is not set; source this script again after Step 8\n' >&2
elif ! _sz_settings=$(cat "$_sz_root/config/engine_config.json"); then
  printf 'senzing-env.sh: could not read %s\n' "$_sz_root/config/engine_config.json" >&2
  unset _sz_self _sz_root _sz_settings
  return 1 2>/dev/null || exit 1
elif [ -z "$_sz_settings" ]; then
  printf 'senzing-env.sh: %s is empty - refusing to export an empty configuration\n' \
    "$_sz_root/config/engine_config.json" >&2
  unset _sz_self _sz_root _sz_settings
  return 1 2>/dev/null || exit 1
else
  export SENZING_ENGINE_CONFIGURATION_JSON="$_sz_settings"
fi

export SENZING_PROJECT_ROOT="$_sz_root"
_sz_brew_prefix=$(brew --prefix) || {
  printf 'senzing-env.sh: brew --prefix failed; cannot resolve SENZING_ROOT\n' >&2
  unset _sz_self _sz_root _sz_settings _sz_brew_prefix
  return 1 2>/dev/null || exit 1
}
export SENZING_ROOT="${_sz_brew_prefix}/opt/senzing/er"
unset _sz_brew_prefix

if [ ! -f "$SENZING_ROOT/lib/libSz.dylib" ]; then
  printf 'senzing-env.sh: expected native library not found at %s/lib/libSz.dylib\n' "$SENZING_ROOT" >&2
  unset _sz_self _sz_root _sz_settings
  return 1 2>/dev/null || exit 1
fi

export DYLD_LIBRARY_PATH="${SENZING_ROOT}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}"
export PATH="${SENZING_ROOT}/bin${PATH:+:${PATH}}"

unset _sz_self _sz_root _sz_settings
