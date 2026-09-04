# OTG Mouse Fix（OTG 鼠标校正 · UI 重制版）

一个修复部分手机 OTG 鼠标方向映射异常（如「向右移动、光标向上」）的 Android 小工具。本项目是原版
「OTG鼠标校正」APK 的界面重制与功能增强版：全部界面代码重写，服务逻辑在保留原语义的基础上修复了
若干竞态与稳定性问题。

## 功能特性

- 卡片式主界面：设备选择 / 控制面板 / 实时运行日志三个分区，右上角状态胶囊实时显示 Shizuku 与接管状态
- 内置完整「使用说明」页：工作原理、前置条件、7 档修正矩阵逐档说明、分步教程、注意事项、常见问题排查
- 7 档修正矩阵（恒等 / 逆时针 90° / 顺时针 90° / 旋转 180° / 交换 XY / 翻转 X / 翻转 Y）
- 4 档合并缓冲（0 关 / 4ms / 8ms / 16ms），平衡平滑度与延迟
- 圆形悬浮球（OFF 灰 / ON 绿）+ 深色悬浮控制面板，游戏内随时切换矩阵、开始/停止
- 基于 Shizuku 无 ROOT 运行，仅部署与运行 `/data/local/tmp/evtool`、枚举与抓取输入设备

## 工作原理

应用通过 Shizuku 获得与 adb（shell）同级的权限，把自带的 evtool 命令行工具部署到
`/data/local/tmp/evtool` 并运行。evtool 独占抓取（EVIOCGRAB）选定的鼠标设备节点，对相对位移按
修正矩阵做几何变换、按时间窗口合并缓冲后，通过内核 uinput 虚拟设备重新注入系统，从而修正光标方向。
停止接管即结束 evtool 进程，原生输入自动恢复。

## 免责声明与致谢（请务必阅读）

- 本项目由对原版闭源应用「OTG鼠标校正」（包名 `com.evfix.validate`）逆向分析而来，`app/src/` 下的
  界面与服务代码为重写/增强实现。
- `vendor/` 与 `native/` 目录下的文件（`AndroidManifest.xml`、`resources.arsc`、`classes*.dex`、
  `res/`、`libevtool.so`）**提取自原版 APK，版权归原作者所有**，在本仓库中仅用于互操作与个人学习目的，
  MIT 许可证不覆盖这些文件。如你是原作者且要求移除，请提 Issue，我会立即处理。
- 本项目仅供学习研究与个人设备使用，请勿用于商业用途；使用本项目造成的任何问题由使用者自行承担。
- 感谢 [Shizuku](https://github.com/RikkaApps/Shizuku)（RikkaApps）提供的无 ROOT 特权执行框架；
  `vendor/classes.dex` 中的 Shizuku 相关接口定义来自其开源代码。

## 目录结构

```
otg-mouse-fix/
├── app/src/com/evfix/validate/   # 重写的应用源码（MIT）
│   ├── MainActivity.java         #   主界面 + 使用说明页
│   └── FixService.java           #   前台服务 + 悬浮球/面板 + evtool 调用
├── vendor/                       # 提取自原版 APK 的资源与依赖（版权归原作者）
│   ├── AndroidManifest.xml       #   二进制清单（minSdk 29 / targetSdk 33）
│   ├── resources.arsc / res/     #   编译期资源（含未引用的 ball/panel 布局）
│   ├── classes.dex               #   Shizuku API 桩（运行时必需，也作编译类路径）
│   └── classes2.dex              #   R 类
├── native/lib/<abi>/libevtool.so # evtool 核心工具（版权归原作者）
├── scripts/
│   ├── make-stubs.sh             # vendor/classes.dex → build/stubs.jar（编译桩）
│   ├── build.sh                  # javac → jar → d8 → build/classes3.dex
│   └── package.sh                # 组装 → zip → zipalign → 签名 → 验证
└── build/                        # 构建产物（不入库）
```

## 构建环境

| 依赖 | 说明 | 获取方式 |
|------|------|----------|
| JDK | 含 javac / jar / keytool（8–17 均可） | [adoptium.net](https://adoptium.net) |
| Android build-tools | 需要 `d8`、`zipalign`、`apksigner`、`core-lambda-stubs.jar` | sdkmanager `"build-tools;34.0.0"` |
| android-34 platform | 需要 `android.jar` | sdkmanager `"platforms;android-34"` |
| dex-tools 2.4 | 需要 `d2j-dex2jar.sh` | [GitHub Releases](https://github.com/pxb1988/dex2jar/releases) |
| python3 | 打包 zip 用 | 系统包管理器 |

无需 Gradle / Android Studio，三个脚本即可完成构建。

## 构建步骤

```bash
# 1. 生成编译桩（从 vendor/classes.dex 提取 Shizuku 接口）
D2J_DIR=/path/to/dex-tools-v2.4 bash scripts/make-stubs.sh

# 2. 编译
BT_DIR=/path/to/build-tools/34.0.0 ANDROID_JAR=/path/to/android-34/android.jar bash scripts/build.sh

# 3. 打包并签名（生成 build/signed.apk）
BT_DIR=/path/to/build-tools/34.0.0 bash scripts/package.sh
```

路径也可以用默认值 `$HOME/tools/...`，见各脚本头部注释。首次打包会在 `build/debug.keystore`
生成调试密钥（storepass/keypass 均为 `android`），正式发布请替换为自己的密钥。

## 安装与使用

1. 安装并激活 [Shizuku](https://shizuku.rikka.app/)（无线调试配对或 ROOT 启动）
2. 安装 `build/signed.apk`，启动后允许 Shizuku 授权
3. 连接 OTG 鼠标 → 点「刷新」→ 选择带 `[鼠标]` 标记的设备
4. 保持默认「修正矩阵 1」点「开始接管」试方向，不对就换档位
5. 应用内右上角「使用说明」有完整的工作原理、逐项设置详解、分步教程与常见问题排查

系统要求 Android 10（API 29）及以上；已覆盖安装测试于 Android 10–13 设备。

## 许可证

`app/src/` 与 `scripts/` 下的代码以 [MIT](LICENSE) 许可发布。
`vendor/` 与 `native/` 下的二进制与资源文件为原作者财产，不受上述许可约束，见「免责声明与致谢」。
