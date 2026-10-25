#!/bin/bash
# Builds Glow into build/glow.apk with the plain Android SDK tools (no Gradle).
# Needs: JDK 17 and the Android SDK (build-tools 34, platform android-35). On macOS: brew openjdk@17 and
# android-commandlinetools; elsewhere (e.g. GitHub Actions) set JAVA_HOME and ANDROID_HOME.
set -euo pipefail
cd "$(dirname "$0")"

if [ -z "${JAVA_HOME:-}" ] && command -v brew >/dev/null; then
    export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
fi
SDK="${ANDROID_HOME:-$(brew --prefix 2>/dev/null)/share/android-commandlinetools}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-35/android.jar"
# Release builds (tools/release or the GitHub workflow) set these from the version tag, e.g. v1.4.0 -> 10400 / 1.4.0.
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-dev}"
# A signing key is created on the first build and stays on your machine (glow.keystore is git-ignored).
KS_PASS="${GLOW_KS_PASS:-glowglow}"

rm -rf build && mkdir -p build/gen build/classes

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml --java build/gen -o build/base.apk \
    --min-sdk-version 29 --target-sdk-version 29 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" build/res.zip

[ -d libs ] || tools/fetch_libs.sh
# WmCatalog.java is generated from World Monitor's channel catalogue (AGPL-3.0, github.com/koala73/worldmonitor),
# so it isn't kept in this repo: it's downloaded and generated on the first build.
[ -f src/com/tv98/glow/WmCatalog.java ] || python3 tools/gen_wm_catalog.py
LIBS=$(ls libs/*.jar | tr '\n' ':')

"$JAVA_HOME/bin/javac" -nowarn -Xlint:-options --release 8 -encoding UTF-8 \
    -classpath "$JAR:$LIBS" -d build/classes $(find src build/gen -name '*.java')

# R8 shrinks the bundled player libraries down to what Glow uses (Guava alone is ~3 MB otherwise).
(cd build/classes && "$JAVA_HOME/bin/jar" cf ../app.jar .)
PG=""
for f in glow.pro libs/*.pro; do PG="$PG --pg-conf $f"; done
"$JAVA_HOME/bin/java" -Xmx2g -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release --min-api 29 \
    --lib "$JAR" --output build $PG build/app.jar libs/*.jar

(cd build && zip -qj base.apk classes*.dex)
"$BT/zipalign" -f 4 build/base.apk build/aligned.apk

if [ ! -f glow.keystore ]; then
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore glow.keystore -alias glow -keyalg RSA -keysize 2048 \
        -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=Glow TV98" >/dev/null 2>&1
fi
"$JAVA_HOME/bin/java" -jar "$BT/lib/apksigner.jar" sign --ks glow.keystore --ks-pass "pass:$KS_PASS" \
    --key-pass "pass:$KS_PASS" --out build/glow.apk build/aligned.apk

ls -l build/glow.apk
