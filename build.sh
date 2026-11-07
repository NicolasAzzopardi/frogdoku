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
# Signature avec rotation de clé (APK Signature Scheme v3) :
#  - Android 9+ : nouvelle clé (frogdoku-2), reconnue grâce à la lignée signée par l'ancienne ;
#  - Android 7/8 : ancienne clé (v1/v2), seule possible sur ces versions.
# Les clés et mots de passe sont hors du dépôt, dans ~/.config/frogdoku/.
KEYS="${FROGDOKU_KEYS_DIR:-$HOME/.config/frogdoku}"
for f in frogdoku.keystore keystore.pass frogdoku-2.keystore keystore2.pass frogdoku.lineage; do
    if [ ! -f "$KEYS/$f" ]; then
        echo "Fichier de signature manquant : $KEYS/$f" >&2
        exit 1
    fi
done
"$BT/apksigner" sign \
    --ks "$KEYS/frogdoku.keystore" --ks-key-alias frogdoku --ks-pass "file:$KEYS/keystore.pass" \
    --next-signer \
    --ks "$KEYS/frogdoku-2.keystore" --ks-key-alias frogdoku2 --ks-pass "file:$KEYS/keystore2.pass" \
    --lineage "$KEYS/frogdoku.lineage" --rotation-min-sdk-version 28 \
    --out Frogdoku.apk "$OUT/aligned.apk"
echo "OK -> $(pwd)/Frogdoku.apk"
