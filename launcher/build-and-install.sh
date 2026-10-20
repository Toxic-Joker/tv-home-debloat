#!/usr/bin/env bash
# Build the launcher with plain SDK tools (no Gradle) and install it on the TV.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=..
SDK=${ANDROID_SDK:-$ROOT/tools/sdk}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-34/android.jar
JAVAC=${JAVAC:-javac}
ADB=${ADB:-$ROOT/tools/platform-tools/adb}; [ -x "$ADB" ] || ADB=adb
TV=${TV:-$(cat $ROOT/.tv 2>/dev/null)}
OUT=build

rm -rf $OUT && mkdir -p $OUT/res $OUT/gen $OUT/classes $OUT/dex

$BT/aapt2 compile --dir res -o $OUT/res/
$BT/aapt2 link -o $OUT/unsigned.apk -I $JAR --manifest AndroidManifest.xml \
  -A assets --java $OUT/gen --min-sdk-version 26 --target-sdk-version 34 \
  -0 jpg $OUT/res/*.flat
$JAVAC --release 11 -nowarn -classpath $JAR -d $OUT/classes \
  $(find src $OUT/gen -name '*.java')
$BT/d8 --release --min-api 26 --lib $JAR --output $OUT/dex $(find $OUT/classes -name '*.class')
cp $OUT/unsigned.apk $OUT/with-dex.apk
(cd $OUT/dex && zip -q -0 ../with-dex.apk classes.dex)
$BT/zipalign -f -p 4 $OUT/with-dex.apk $OUT/aligned.apk

KS=$ROOT/tools/launcher.keystore
[ -f $KS ] || keytool -genkeypair -keystore $KS -storepass tvhome -keypass tvhome \
  -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=TV Home" >/dev/null 2>&1
$BT/apksigner sign --ks $KS --ks-pass pass:tvhome --key-pass pass:tvhome --out tv-home.apk $OUT/aligned.apk
ls -lh tv-home.apk

if [ "${1:-}" != "--no-install" ]; then
  $ADB connect $TV >/dev/null
  $ADB -s $TV install -r tv-home.apk
fi
