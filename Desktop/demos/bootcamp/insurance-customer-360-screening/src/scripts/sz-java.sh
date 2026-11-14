#!/usr/bin/env bash
# sz-java.sh — compile and run this project's Java programs against the Senzing SDK.
#
# Sources the project environment and launches the JVM in ONE process, because on
# macOS DYLD_LIBRARY_PATH must be in the environment of the shell that starts java
# (-Djava.library.path alone is insufficient — Senzing MCP sdk_guide, macos_arm/java).
#
# Usage:
#   src/scripts/sz-java.sh compile <source.java> [more.java ...]
#   src/scripts/sz-java.sh run <ClassName> [args ...]
#
# Classes compile into build/classes. Flags are written out explicitly rather than
# passed through an unquoted variable (zsh does not word-split those).

_here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=/dev/null
source "$_here/senzing-env.sh" || exit 1

_classes="$SENZING_PROJECT_ROOT/build/classes"
mkdir -p "$_classes"

# Call the JDK's own binaries, never /usr/bin/java. On macOS /usr/bin/java and
# /usr/bin/javac are Apple's SIP-protected launcher stubs, and macOS strips DYLD_*
# variables from protected binaries, so the JVM would never see DYLD_LIBRARY_PATH
# (observed: UnsatisfiedLinkError "no Sz in java.library.path").
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  printf 'sz-java.sh: cannot locate a JDK (JAVA_HOME=%s)\n' "${JAVA_HOME:-unset}" >&2
  exit 1
fi

case "${1:-}" in
  compile)
    shift
    exec "$JAVA_HOME/bin/javac" -Xlint:all -cp "$SENZING_SDK_JAR" -d "$_classes" "$@"
    ;;
  run)
    shift
    exec "$JAVA_HOME/bin/java" -cp "$SENZING_SDK_JAR:$_classes" "$@"
    ;;
  *)
    printf 'usage: %s compile <files.java> | run <ClassName> [args]\n' "$0" >&2
    exit 2
    ;;
esac
