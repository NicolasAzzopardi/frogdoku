#!/usr/bin/env bash
# Construit Frogdoku.apk sans Gradle, avec les outils du SDK Android.
set -euo pipefail
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
SRC=app/src/main
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT"/{res,gen,classes,dex}

"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/res/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest "$SRC/AndroidManifest.xml" -A "$SRC/assets" \
    --java "$OUT/gen" -o "$OUT/unsigned.apk" "$OUT/res/res.zip"

javac --release 11 -nowarn -encoding UTF-8 -classpath "$JAR" -d "$OUT/classes" \
    $(find "$SRC/java" "$OUT/gen" -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)

"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KS="${FROGDOKU_KEYSTORE:-frogdoku.keystore}"
PASS_FILE="${FROGDOKU_KS_PASS_FILE:-$HOME/.config/frogdoku/keystore.pass}"
if [ ! -f "$KS" ] || [ ! -f "$PASS_FILE" ]; then
    echo "Clé de signature introuvable : $KS et/ou $PASS_FILE" >&2
    echo "(le mot de passe n'est volontairement pas stocké dans le projet)" >&2
    exit 1
fi
"$BT/apksigner" sign --ks "$KS" --ks-key-alias frogdoku \
    --ks-pass "file:$PASS_FILE" \
    --out Frogdoku.apk "$OUT/aligned.apk"
echo "OK -> $(pwd)/Frogdoku.apk"
