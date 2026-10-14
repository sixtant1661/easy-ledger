# 简单记账助手 · Easy Ledger

面向中老年人的工作记账与结算 Android APP。当前里程碑为基础工程预览：中文首页、项目列表占位、设置、三页导航及可展开使用说明。**尚无 Room、项目创建、记账、日期/时间输入或文件导出功能。**

## 本次文件与目录

- `docs/PRD.md`、`docs/ARCHITECTURE.md`：八条暂定业务政策，均为“待产品方最终确认”；跨午夜模型保存完整起止日期时间。
- `docs/ROADMAP.md`、`AGENTS.md`：阶段状态、三星验证与开发约束。
- 根目录 Gradle Kotlin DSL、`gradle/libs.versions.toml`、完整 Gradle Wrapper、`.gitignore`：固定构建配置。
- `app/`：Manifest、中文资源、启动图标、Activity、主题、公共卡片、导航、首页 ViewModel、三页 UI、JVM 和设备测试。
- `domain/` 与 `data/` 的 README：MVVM 分层边界预留，不包含虚构业务实现。
- `scripts/setup-cloud.py`、`scripts/cloud-build.py`：已准备云快照的代理/证书/可写目录适配，不适用于普通本地构建。
- `docs/BUILD_STATUS.md`：实际验证结果与限制。

## 固定工具链

| 组件 | 版本 |
| --- | --- |
| AGP | 8.9.2 |
| Gradle Wrapper | 8.11.1（启用官方 SHA-256 分发校验） |
| Kotlin / Compose 编译器插件 | 2.1.20 |
| Compose BOM | 2025.04.01 |
| Material 3 | 1.3.2（由 BOM 锁定） |
| Activity Compose | 1.10.1 |
| Lifecycle | 2.8.7 |
| Navigation Compose | 2.8.9 |
| Coroutines | 1.10.1 |
| JUnit | 4.13.2 |
| AndroidX Test JUnit / Espresso | 1.2.1 / 3.6.1 |
| 编译/目标 SDK | 35 / 35 |
| Java/Kotlin 字节码目标 | 17 |

云环境采用完整 Temurin JDK 17.0.18+8；原有 JRE 21 缺少 javac，不能用于完整构建。AGP 8.9 对应 Gradle 8.11.1、JDK 17 及 API 35；本次选择保守的固定兼容组合，后续发布时再评估 targetSdk 要求。

`minSdk 23` 仅是暂定技术构建下限，**不是已确认的最低产品支持版本**。妈妈三星型号、Android 与 One UI 版本待确认；应用 ID `com.easyledger.app` 也是开发阶段命名，正式发布前需确认。

## 本地构建

1. 安装支持该 AGP 的 Android Studio 和完整 JDK 17，设置 Gradle JDK 为 JDK 17。
2. 在 SDK Manager 安装 Android SDK Platform 35、Build Tools 35.0.0、Platform Tools。安装 Command-line Tools 并按需要接受 SDK 许可证。
3. 使用 Android Studio 打开仓库根目录并同步。IDE 可创建被忽略的 `local.properties`；也可设置 `ANDROID_HOME` 为实际 SDK 路径。SDK 路径和密钥不提交 Git。
4. macOS/Linux 运行：

```sh
export JAVA_HOME="<完整 JDK 17 目录>"
export ANDROID_HOME="<Android SDK 目录>"
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Windows 使用 `gradlew.bat`，在系统或终端配置相应变量。初次构建需联网下载依赖；APP 本身未申请 INTERNET 权限。

预期 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。连接设备并授权 USB 调试后，可执行：

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest
```

无 SDK 时 Gradle 无法构建；无设备/模拟器时不能执行设备测试。设备测试 APK 构建成功也不代表设备测试已经运行。

## 云环境构建

环境快照中 SDK、完整 JDK 和依赖在仓库外保留，不把工具链、云代理或证书路径写入项目级 Gradle 配置。SDK 或 JDK 缺失时先运行 `python3 scripts/setup-cloud.py`（Linux 云环境专用，下载及校验固定工具链）。准备完成后运行：

```sh
python3 scripts/cloud-build.py
```

此脚本保持 TLS 验证，使用平台证书的独立信任库以及可写的 Android/Gradle 目录；不输出代理凭据。可追加 Gradle 任务，例如 `python3 scripts/cloud-build.py connectedDebugAndroidTest`。

## 界面与三星验收

Corporate Clean 固定色系，常规正文 18sp，主按钮最小高度 56dp，页面滚动容纳放大字体。Material 按钮和底部导航提供 ripple 与选中反馈；使用系统窗口边距处理手势和三键导航。导航返回首页时保留使用说明状态，SavedStateHandle 支持恢复。

请在目标三星手机核对：最大字体/屏幕缩放、竖横屏、TalkBack、手势/三键导航和系统返回。后续日期选择、时间选择、SAF 文件导出及微信分享有单独实机验收，不在当前版本中冒充可用。

本版本不用账号、广告或数据采集 SDK，不申请网络或存储权限，系统备份暂关闭；没有业务数据库，也没有备份恢复承诺。仅可作为基础页面验收版。

项目规范见 [AGENTS.md](AGENTS.md)，需求见 [PRD](docs/PRD.md)。完成此里程碑后暂停，等待产品方验收，再进入领域计算/项目管理/Room 开发。
