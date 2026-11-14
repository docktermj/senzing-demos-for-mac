# senzing-env.sh — project-local Senzing environment (source it; do not execute it).
#   source src/scripts/senzing-env.sh
# Never edits ~/.zshrc or any global shell config. Platform: macOS Apple Silicon (Homebrew cask).
# Variable values from sdk_guide(topic='install', platform='macos_arm', language='java').

# --- resolve this script's own location (bash and zsh) ------------------------
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

# --- platform exports (macos_arm) ---------------------------------------------
export SENZING_ROOT="$(brew --prefix)/opt/senzing/er"
export DYLD_LIBRARY_PATH="${SENZING_ROOT}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}"
export PATH="${SENZING_ROOT}/bin:${PATH}"
# Java SDK jar — path from the macos_arm Java gotcha: ${SENZING_ROOT}/sdk/java/sz-sdk.jar
export SZ_SDK_JAR="${SENZING_ROOT}/sdk/java/sz-sdk.jar"
# The JVM to launch with. /usr/bin/java is a system stub that drops DYLD_LIBRARY_PATH before
# the real JVM starts (observed on this Mac, 2026-11-14: "no Sz in java.library.path"), so use
# the JDK binary directly. Observation, not an MCP-sourced fact.
export SZ_JAVA="$(/usr/libexec/java_home)/bin/java"

unset _sz_self _sz_root _sz_settings
