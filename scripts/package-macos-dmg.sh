#!/usr/bin/env bash
set -euo pipefail

APP_NAME="Wuziqi"
APP_VERSION="1.0.0"
MAIN_CLASS="com.example.wuziqi.WuziqiApp"
MAIN_JAR="wuziqi-1.0-SNAPSHOT.jar"
DIST_DIR="target/dist/macos"
PACKAGE_INPUT_DIR="target/jpackage-input"

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/jpackage" ]]; then
  JPACKAGE="$JAVA_HOME/bin/jpackage"
elif command -v jpackage >/dev/null 2>&1; then
  JPACKAGE="$(command -v jpackage)"
else
  echo "jpackage was not found. Please run this script with JDK 25+ on PATH or JAVA_HOME." >&2
  exit 1
fi

if ! command -v mvnd >/dev/null 2>&1; then
  echo "mvnd was not found. Please install mvnd or replace mvnd with mvn in this script." >&2
  exit 1
fi

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "macOS DMG packages must be created on macOS." >&2
  exit 1
fi

rm -rf "$DIST_DIR"
rm -rf "$PACKAGE_INPUT_DIR"
mkdir -p "$DIST_DIR"
mkdir -p "$PACKAGE_INPUT_DIR/lib"

mvnd -DskipTests package
mvnd -DincludeScope=runtime dependency:copy-dependencies -DoutputDirectory="$PACKAGE_INPUT_DIR/lib"
cp "target/$MAIN_JAR" "$PACKAGE_INPUT_DIR/$MAIN_JAR"

"$JPACKAGE" \
  --type dmg \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "FXGL" \
  --dest "$DIST_DIR" \
  --input "$PACKAGE_INPUT_DIR" \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  --java-options "-Dfile.encoding=UTF-8"

echo "Created macOS DMG in $DIST_DIR"
