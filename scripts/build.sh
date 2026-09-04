#!/bin/bash
# 编译 app 源码 → build/classes3.dex
# 依赖：
#   Android platforms android-34 的 android.jar（提供 ANDROID_JAR）
#   build-tools（含 d8、core-lambda-stubs.jar，提供 BT_DIR）
set -e
cd "$(dirname "$0")/.."

AJ="${ANDROID_JAR:-$HOME/tools/android-34/android.jar}"
BT="${BT_DIR:-$HOME/tools/build-tools/34.0.0}"

for f in "$AJ" "$BT/d8" "$BT/core-lambda-stubs.jar"; do
  if [ ! -f "$f" ]; then
    echo "缺少构建依赖: $f（可用 ANDROID_JAR / BT_DIR 环境变量指定）"
    exit 1
  fi
done

echo "== 1. javac =="
rm -rf build/out
mkdir -p build/out
javac -source 8 -target 8 -nowarn \
  -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" \
  -cp build/stubs.jar \
  -d build/out \
  app/src/com/evfix/validate/MainActivity.java app/src/com/evfix/validate/FixService.java
find build/out -name "*.class" | sort

echo "== 2. jar =="
rm -f build/app.jar
jar cf build/app.jar -C build/out .

echo "== 3. d8 =="
rm -f build/classes.dex build/classes3.dex
"$BT/d8" --release --min-api 29 --lib "$AJ" --output build build/app.jar
mv build/classes.dex build/classes3.dex
ls -la build/classes3.dex
echo BUILD_DONE
