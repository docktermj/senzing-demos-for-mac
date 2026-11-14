#!/usr/bin/env zsh
# run-java.sh — launch a compiled bootcamp Java program with the Senzing environment in force.
# DYLD_LIBRARY_PATH must be set in the shell that starts the JVM (-Djava.library.path is not
# enough on macOS), so this script sources the env script and then starts java in one step.
#
#   src/scripts/run-java.sh <classes-dir> <main-class> [args...]
source "${0:A:h}/senzing-env.sh" || exit 1
classes="$1"; shift
exec "${SZ_JAVA}" --enable-native-access=ALL-UNNAMED -cp "${SZ_SDK_JAR}:${classes}" "$@"
