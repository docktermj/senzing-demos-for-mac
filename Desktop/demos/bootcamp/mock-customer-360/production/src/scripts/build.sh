#!/bin/sh
# Compiles every Java source into src/build/classes (plain javac, no external dependencies).
# Usage: src/scripts/build.sh
_here=$(cd -- "$(dirname -- "$0")" && pwd)
. "$_here/senzing-env.sh" || exit 1
cd "$SENZING_PROJECT_ROOT" || exit 1
mkdir -p src/build/classes
exec "$SENZING_JAVA_HOME/bin/javac" -Xlint:all -d src/build/classes -cp "$SENZING_SDK_JAR" \
  src/server/Json.java src/transform/*.java src/load/*.java src/query/*.java
