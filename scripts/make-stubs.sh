#!/bin/bash
# 从原版 APK 的 classes.dex（Shizuku API）生成编译用的桩 jar
# 依赖 dex2jar (dex-tools 2.4)：https://github.com/pxb1988/dex2jar/releases
set -e
cd "$(dirname "$0")/.."

D2J="${D2J_DIR:-$HOME/tools/dex-tools-v2.4}"
if [ ! -x "$D2J/d2j-dex2jar.sh" ]; then
  echo "未找到 d2j-dex2jar.sh，请用 D2J_DIR 环境变量指定 dex-tools 目录"
  exit 1
fi

mkdir -p build
"$D2J/d2j-dex2jar.sh" -f -o build/stubs.jar vendor/classes.dex
echo "STUBS_OK: build/stubs.jar"
