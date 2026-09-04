#!/bin/bash
# 打包签名 APK：vendor 资源 + native 二进制 + build/classes3.dex → build/signed.apk
# 依赖 build-tools（zipalign / apksigner）
set -e
cd "$(dirname "$0")/.."

BT="${BT_DIR:-$HOME/tools/build-tools/34.0.0}"
KS="${KS_FILE:-build/debug.keystore}"

if [ ! -f build/classes3.dex ]; then
  echo "缺少 build/classes3.dex，请先运行 scripts/build.sh"
  exit 1
fi

echo "== 1. assemble =="
rm -rf build/apk
mkdir -p build/apk/res/layout
cp vendor/AndroidManifest.xml build/apk/
cp vendor/classes.dex build/apk/
cp vendor/classes2.dex build/apk/
cp build/classes3.dex build/apk/
cp vendor/resources.arsc build/apk/
cp vendor/res/layout/*.xml build/apk/res/layout/
cp -r native/lib build/apk/lib
find build/apk -type f | sort

echo "== 2. zip =="
rm -f build/base.apk
python3 - "$PWD/build/apk" "$PWD/build/base.apk" <<'EOF'
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
        # resources.arsc 必须按 AAPT 规范不压缩存储
        z.write(full, rel, compress_type=zipfile.ZIP_STORED if rel == 'resources.arsc' else zipfile.ZIP_DEFLATED)
    print('zip entries:', len(names))
EOF
ls -la build/base.apk

echo "== 3. zipalign =="
"$BT/zipalign" -f 4 build/base.apk build/aligned.apk
"$BT/zipalign" -c 4 build/aligned.apk && echo ALIGN_OK

echo "== 4. keystore =="
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

echo "== 5. sign =="
rm -f build/signed.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --min-sdk-version 29 --max-sdk-version 34 --out build/signed.apk build/aligned.apk

echo "== 6. verify =="
"$BT/apksigner" verify --print-certs build/signed.apk | head -5
ls -la build/signed.apk
echo PACKAGE_DONE
