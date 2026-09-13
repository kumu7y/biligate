#!/bin/bash
set -e
cd "$(dirname "$0")"
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*"
W=$(pwd -W)   # E:/hook/clipdirect2
SZ="/g/Program Files/7-Zip/7z.exe"

JDK="G:\Program Files\Java\jdk-21\bin"
BT="G:\Android-sdk\build-tools\35.0.0"
AJ="G:\Android-sdk\platforms\android-35\android.jar"
API="E:\hook\clipdirect2\libs\api\classes.jar"
OUT=build

rm -rf $OUT
mkdir -p $OUT/classes

"$JDK/javac.exe" -encoding UTF-8 -cp "$AJ;$API" -d $OUT/classes \
  src/com/kumu7y/biligate/MainHook.java

"$BT/d8.bat" --release --min-api 29 --lib "$AJ" \
  --output "$W\build" $(find $OUT/classes -name '*.class')

"$BT/aapt2.exe" link -I "$AJ" --manifest AndroidManifest.xml -o "$W\build\unsigned.apk"

SZP=$(cygpath -w "$(command -v 7z 2>/dev/null || echo /g/Program\ Files/7-Zip/7z.exe)")
(cd $OUT && "$SZ" a -tzip unsigned.apk classes.dex >/dev/null)
mkdir -p $OUT/apkroot/META-INF/xposed
cp module.prop java_init.list scope.list $OUT/apkroot/META-INF/xposed/
(cd $OUT/apkroot && "$SZ" a -tzip "$W\build\unsigned.apk" META-INF/xposed/module.prop META-INF/xposed/java_init.list META-INF/xposed/scope.list > /dev/null)

if [ ! -f debug.keystore ]; then
  "$JDK/keytool.exe" -genkeypair -keystore debug.keystore -storepass android \
    -keypass android -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000
fi
"$BT/zipalign.exe" -f 4 "$W\build\unsigned.apk" "$W\build\aligned.apk"
"$BT/apksigner.bat" sign --ks debug.keystore --ks-pass pass:android \
  --key-pass pass:android --out "$W\build\BilibiliRedirect.apk" "$W\build\aligned.apk"
echo "DONE: $W/build/BilibiliRedirect.apk"
