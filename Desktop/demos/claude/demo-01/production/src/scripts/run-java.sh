#!/bin/sh
# Compile-free launcher: sources the project's Senzing environment, then starts the
# JVM in the same process so DYLD_LIBRARY_PATH reaches it (sdk_guide macos_arm Java
# gotcha: -Djava.library.path alone is insufficient).
#
# Usage: src/scripts/run-java.sh <main.Class> [args...]
# Classpath: the SDK jar, the project's compiled classes, and any jars in src/lib.
_here=$(cd -- "$(dirname -- "$0")" && pwd)
. "$_here/senzing-env.sh" || exit 1
_cp="$SENZING_SDK_JAR:$SENZING_PROJECT_ROOT/src/build/classes"
for _j in "$SENZING_PROJECT_ROOT"/src/lib/*.jar; do
  [ -f "$_j" ] && _cp="$_cp:$_j"
done
exec "$SENZING_JAVA_HOME/bin/java" --enable-native-access=ALL-UNNAMED -cp "$_cp" "$@"
