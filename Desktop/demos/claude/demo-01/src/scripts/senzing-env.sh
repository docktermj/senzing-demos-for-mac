# Project-local Senzing environment for this bootcamp (macOS Apple Silicon, Java).
# Source it, do not execute it:   source src/scripts/senzing-env.sh
#
# Values come from sdk_guide(topic='install', platform='macos_arm', language='java'):
#   env_vars:  SENZING_ROOT, DYLD_LIBRARY_PATH, PATH
#   gotchas[]: Java classpath  ${SENZING_ROOT}/sdk/java/sz-sdk.jar
#   gotchas[]: the 4.3 cask lacks rpath entries for sqlite and openssl@3, so their
#              Homebrew lib dirs are added to DYLD_LIBRARY_PATH (installed: 4.3.2.26162).
# This script never touches ~/.zshrc or any other global shell config.

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
# config/bootcamp_progress.json exists from project setup on, so it marks the root
# at every step this script is sourced from (engine_config.json waits for Step 8).
if [ ! -f "$_sz_root/config/bootcamp_progress.json" ]; then
  printf 'senzing-env.sh: resolved project root has no config/bootcamp_progress.json\n' >&2
  printf 'senzing-env.sh:   resolved root: %s\n' "$_sz_root" >&2
  printf 'senzing-env.sh:   this is a path-resolution fault, not your Senzing install\n' >&2
  unset _sz_self _sz_root
  return 1 2>/dev/null || exit 1
fi

# --- engine configuration: skip while absent, never export an empty one -----
if [ ! -f "$_sz_root/config/engine_config.json" ]; then
  # Before Step 8 writes it: leave the variable unset and keep going.
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

# --- platform exports (macos_arm) ---------------------------------------------
_sz_brew=$(brew --prefix)
export SENZING_ROOT="$_sz_brew/opt/senzing/er"
export DYLD_LIBRARY_PATH="${SENZING_ROOT}/lib:$_sz_brew/opt/sqlite/lib:$_sz_brew/opt/openssl@3/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
export PATH="${SENZING_ROOT}/bin:${PATH}"

# --- Java -------------------------------------------------------------------------
# The SDK jar (bindings) lives under sdk/java; the native library lives under lib.
export SENZING_SDK_JAR="${SENZING_ROOT}/sdk/java/sz-sdk.jar"
# Launch the real JDK binary, not the /usr/bin/java stub: macOS strips DYLD_*
# variables when it starts a protected /usr/bin program, so the stub would launch
# the JVM without DYLD_LIBRARY_PATH (environment observation, macOS 26, JDK 25).
export SENZING_JAVA_HOME="$(/usr/libexec/java_home)"

unset _sz_self _sz_root _sz_settings _sz_brew
