if [ -n "${ZSH_VERSION:-}" ]; then
  _sz_self=${(%):-%x}
else
  _sz_self=${BASH_SOURCE[0]:-$0}
fi
_sz_root=$(cd -- "$(dirname -- "$_sz_self")/../.." && pwd)

if [ -f "$_sz_root/.env" ]; then
  set -a
  . "$_sz_root/.env" || {
    set +a
    printf 'senzing-env.sh: could not load %s\n' "$_sz_root/.env" >&2
    unset _sz_self _sz_root
    return 1 2>/dev/null || exit 1
  }
  set +a
fi

if [ -z "${SENZING_ENGINE_CONFIGURATION_JSON:-}" ]; then
  printf 'senzing-env.sh: set SENZING_ENGINE_CONFIGURATION_JSON in .env or your shell\n' >&2
  unset _sz_self _sz_root
  return 1 2>/dev/null || exit 1
fi

if [ -z "${SENZING_ROOT:-}" ] && command -v brew >/dev/null 2>&1; then
  _sz_brew_prefix=$(brew --prefix 2>/dev/null) || _sz_brew_prefix=
  if [ -n "$_sz_brew_prefix" ]; then
    SENZING_ROOT="${_sz_brew_prefix}/opt/senzing/er"
  fi
fi

if [ -z "${SENZING_ROOT:-}" ]; then
  printf 'senzing-env.sh: set SENZING_ROOT to your Senzing installation directory\n' >&2
  unset _sz_self _sz_root _sz_brew_prefix
  return 1 2>/dev/null || exit 1
fi

export SENZING_PROJECT_ROOT="$_sz_root"
export SENZING_ROOT
export PATH="${SENZING_ROOT}/bin${PATH:+:${PATH}}"
if [ -d "${SENZING_ROOT}/lib" ]; then
  export DYLD_LIBRARY_PATH="${SENZING_ROOT}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}"
fi

unset _sz_self _sz_root _sz_brew_prefix
