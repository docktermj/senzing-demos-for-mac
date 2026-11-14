# senzing-env.sh — project-local Senzing environment for this bootcamp project.
#
# SOURCE this file (do not execute it), in the same shell that launches Java:
#     source src/scripts/senzing-env.sh
#
# It never edits your global shell configuration (~/.zshrc etc.). If you want these
# variables in every shell, adding them to your profile is your choice to make by hand.
#
# Platform values come from the Senzing MCP server:
#   sdk_guide(topic='install', platform='macos_arm', language='java')
#   -> env_vars: SENZING_ROOT, DYLD_LIBRARY_PATH, PATH
#   -> Java gotcha: DYLD_LIBRARY_PATH must be set at the shell level before the JVM
#      starts; classpath uses ${SENZING_ROOT}/sdk/java/sz-sdk.jar
# SENZING_SDK_JAR is derived from that classpath example (not a server-defined variable).

# --- resolve this script's own location (bash and zsh) ------------------------
# ${BASH_SOURCE[0]} is bash-only and expands to EMPTY under zsh, macOS's default
# shell, so branch rather than assume bash. bash parses the zsh-only expansion in
# the untaken branch without complaint.
if [ -n "${ZSH_VERSION:-}" ]; then
  _sz_self=${(%):-%x}                # zsh: this file's own path
else
  _sz_self=${BASH_SOURCE[0]:-$0}     # bash: this file's own path
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

# --- platform exports: macOS Apple Silicon (Homebrew cask) --------------------
export SENZING_ROOT="$(brew --prefix)/opt/senzing/er"
if [ ! -f "$SENZING_ROOT/lib/libSz.dylib" ]; then
  printf 'senzing-env.sh: no libSz.dylib under SENZING_ROOT=%s\n' "$SENZING_ROOT" >&2
fi
export DYLD_LIBRARY_PATH="${SENZING_ROOT}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}"
export PATH="${SENZING_ROOT}/bin:${PATH}"
export SENZING_SDK_JAR="${SENZING_ROOT}/sdk/java/sz-sdk.jar"

unset _sz_self _sz_root _sz_settings
