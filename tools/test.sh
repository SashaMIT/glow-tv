#!/bin/bash
# Runs Glow's tests on this computer, no box needed: the logic that doesn't touch the screen, on plain Java.
# Usage: tools/test.sh   (after ./build.sh, which leaves the compiled classes in build/classes)
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && command -v brew >/dev/null; then
    export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
fi
SDK="${ANDROID_HOME:-$(brew --prefix 2>/dev/null)/share/android-commandlinetools}"
JAR="$SDK/platforms/android-35/android.jar"
[ -d build/classes/com/tv98/glow ] || ./build.sh >/dev/null
JSON=test/lib/json-20240303.jar # android.jar's org.json only throws "Stub!"
if [ ! -f "$JSON" ]; then
    mkdir -p test/lib
    curl -fsSL -o "$JSON" https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar
fi
OUT=$(mktemp -d)
"$JAVA_HOME/bin/javac" -nowarn -encoding UTF-8 -d "$OUT" -cp "build/classes:$JSON:$JAR" \
    $(find test/stubs test/com -name '*.java')
"$JAVA_HOME/bin/java" -Duser.timezone=Asia/Bangkok -Duser.language=en -cp "$OUT:build/classes:$JSON:$JAR" com.tv98.glow.GlowTests test/data
