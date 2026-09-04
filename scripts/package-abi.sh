#!/bin/bash
# 打包单架构签名 APK：scripts/package-abi.sh <abi> [输出文件]
# 示例：scripts/package-abi.sh arm64-v8a build/OTG-MouseFix-arm64-v8a.apk
set -e
cd "$(dirname "$0")/.."

ABI="${1:?用法: package-abi.sh <arm64-v8a|armeabi-v7a|x86_64|x86> [输出文件]}"
OUT="${2:-build/OTG-MouseFix-${ABI}.apk}"
BT="${BT_DIR:-$HOME/tools/build-tools/34.0.0}"
KS="${KS_FILE:-build/debug.keystore}"

if [ ! -f build/classes3.dex ]; then
  echo "缺少 build/classes3.dex，请先运行 scripts/build.sh"
  exit 1
fi
if [ ! -f "native/lib/${ABI}/libevtool.so" ]; then
  echo "未找到该架构的二进制: native/lib/${ABI}/libevtool.so"
  exit 1
fi

STAGE="build/apk-${ABI}"
rm -rf "$STAGE"
mkdir -p "$STAGE/res/layout"
cp vendor/AndroidManifest.xml "$STAGE/"
cp vendor/classes.dex "$STAGE/"
cp vendor/classes2.dex "$STAGE/"
cp build/classes3.dex "$STAGE/"
cp vendor/resources.arsc "$STAGE/"
cp vendor/res/layout/*.xml "$STAGE/res/layout/"
mkdir -p "$STAGE/lib/${ABI}"
cp "native/lib/${ABI}/libevtool.so" "$STAGE/lib/${ABI}/"

echo "== zip (${ABI}) =="
rm -f build/base-${ABI}.apk
python3 - "$PWD/$STAGE" "$PWD/build/base-${ABI}.apk" <<'EOF'
import os, sys, zipfile
root, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    names = []
    for dirpath, dirnames, filenames in os.walk(root):
        for f in filenames:
            full = os.path.join(dirpath, f)
            names.append(os.path.relpath(full, root))
    names.sort(key=lambda n: (n == 'resources.arsc', n))
    for rel in names:
        full = os.path.join(root, rel)
        z.write(full, rel, compress_type=zipfile.ZIP_STORED if rel == 'resources.arsc' else zipfile.ZIP_DEFLATED)
    print('zip entries:', len(names))
EOF

echo "== zipalign =="
"$BT/zipalign" -f 4 "build/base-${ABI}.apk" "build/aligned-${ABI}.apk"
"$BT/zipalign" -c 4 "build/aligned-${ABI}.apk" && echo ALIGN_OK

echo "== keystore =="
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

echo "== sign =="
rm -f "$OUT"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --min-sdk-version 29 --max-sdk-version 34 --out "$OUT" "build/aligned-${ABI}.apk"
"$BT/apksigner" verify --print-certs "$OUT" | head -2
ls -la "$OUT"
echo "PACKAGE_${ABI}_DONE"
