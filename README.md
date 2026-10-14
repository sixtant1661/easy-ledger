# 简单记账助手 · Easy Ledger

面向中老年人的工作记账与结算 Android APP。当前为 0.2.0 本地工作记账测试版：项目与每日记录创建/修改/确认删除、金额核对、日期选择、历史记录、项目/记录搜索和首次新手引导。Room 将真实输入保存在应用私有数据库，不需要网络、账号或服务器。工时段输入、导出和备份恢复尚未实现。

## 本次文件与目录

- `docs/PRD.md`、`docs/ARCHITECTURE.md`：八条暂定业务政策，均为“待产品方最终确认”；跨午夜模型保存完整起止日期时间。
- `docs/ROADMAP.md`、`AGENTS.md`：阶段状态、三星验证与开发约束。
- 根目录 Gradle Kotlin DSL、`gradle/libs.versions.toml`、完整 Gradle Wrapper、`.gitignore`：固定构建配置。
- `app/`：Manifest、中文资源、启动图标、Activity、主题、公共卡片、导航、首页 ViewModel、三页 UI、JVM 和设备测试。
- `domain/`、`data/`：精确十进制领域计算、Room 实体/DAO/迁移及仓库。
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
| Room / KSP | 2.7.2 / 2.1.20-2.0.1 |
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

预期 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。仅在空白测试设备/模拟器上运行设备套件，不在有业务数据的手机上执行。连接测试设备并授权 USB 调试后，可执行：

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.easyledger.app.UpdatePersistenceTest
```

无 SDK 时 Gradle 无法构建；无设备/模拟器时不能执行设备测试。设备测试 APK 构建成功也不代表设备测试已经运行。

## 云环境构建

环境快照中 SDK、完整 JDK 和依赖在仓库外保留，不把工具链、云代理或证书路径写入项目级 Gradle 配置。SDK 或 JDK 缺失时先运行 `python3 scripts/setup-cloud.py`（Linux 云环境专用，下载及校验固定工具链）。准备完成后运行：

```sh
python3 scripts/cloud-build.py
```

此脚本保持 TLS 验证，使用平台证书的独立信任库以及可写的 Android/Gradle 目录；不输出代理凭据。可追加 Gradle 任务，例如 `python3 scripts/cloud-build.py connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.easyledger.app.UpdatePersistenceTest`。

## 界面与三星验收

Corporate Clean 固定色系，常规正文 18sp，主按钮最小高度 56dp，页面滚动容纳放大字体。Material 按钮和底部导航提供 ripple 与选中反馈；使用系统窗口边距处理手势和三键导航。导航返回首页时保留使用说明状态，SavedStateHandle 支持恢复。

请在目标三星手机核对：最大字体/屏幕缩放、竖横屏、TalkBack、手势/三键导航和系统返回。后续日期选择、时间选择、SAF 文件导出及微信分享有单独实机验收，不在当前版本中冒充可用。

本版本不用账号、广告或数据采集 SDK，不申请网络或存储权限。业务数据库在应用私有存储，系统备份暂关闭；尚无导出备份恢复能力。

项目规范见 [AGENTS.md](AGENTS.md)，需求见 [PRD](docs/PRD.md)。完成本轮后等待产品方验收，下一阶段优先备份恢复。

## 0.2 本轮使用方式与计费规则

1. 首次打开跟随三步引导，或点“跳过”；之后不自动重复。设置可以重新查看。
2. 项目页创建项目，填写名称、默认计费方式、单位、单价和备注。
3. 首页“新增工作记录”，选项目、日期，填写数量/单价/备注；金额自动显示，再点“核对并保存”和“确认保存”。
4. 首页“历史记录”可搜索、查看、编辑或确认删除。项目页也有名称/备注搜索与一键清空。

已确认：数量大于 0、单价可为 0、不支持负数冲销；小时数量最多两位小数；按天手动输入，不从工时换算。金额精确十进制 HALF_UP 两位，各行金额相加。修改项目单价不改变历史记录；修改记录数量会重算金额。疑似同项目/日期/数量/单价重复时提醒，允许继续保存。

Room 2.7.2 + KSP 2.1.20-2.0.1；数据库 v2，显式 v1→v2 迁移添加项目备注，schema JSON 随源码保存。v1 是兼容性测试基线，第一版框架没有数据库，并非曾发布的用户数据库。没有 destructive migration。删除为软删除，项目删除不级联丢失记录；当前没有撤销入口。

## 安装升级与数据风险

目标设备：Samsung Galaxy S21 FE / Android 16 / One UI 8.0；用户确认 0.1 基础版正常，新版完整流程待手机验收。

GitHub Actions 已增加 Debug 签名指纹守卫。重复构建复核发现默认密钥目录与缓存目录不匹配，签名未成功保留，后续构建已被守卫拒绝。修复进度见 BUILD_STATUS，不能宣称跨构建升级签名已就绪。**旧 0.1 框架包由临时签名构建，不能保证直接覆盖安装；它未保存业务数据。首次安装 0.2 若提示签名不匹配，只对这个无数据旧框架版卸载后安装。**

从 0.2 起请直接安装同签名新 APK 覆盖更新，不卸载、不清除数据。缓存丢失会拒绝构建更新包，不能通过改指纹/清缓存/换密钥规避；需恢复原签名。缓存可能过期，不等同永久密钥管理；长期使用前必须配置安全的固定签名并实现备份恢复。

卸载、清除应用数据、手机丢失或故障都会造成记录丢失。Room schema 与 soft delete 不能替代备份。系统自动备份当前关闭，未实现导出备份前不要将 APP 作为长期正式记账的唯一副本。

手机验证：建项目并保存一条数量 2、单价 25 的记录，金额应为 50.00；完全关闭 APP 再打开，或重启手机，检查项目和记录仍存在。测试搜索、编辑、取消删除及确认删除。以后正常覆盖安装后再检查原记录，不要先卸载。

## 测试与备份规划

常规设备测试：`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.easyledger.app.UpdatePersistenceTest`。覆盖安装检查仅在测试模拟器运行 `python3 scripts/verify-update-persistence.py`，使用合成数据，不在生产记账手机上跑整套测试。

Actions 运行 JVM 单元测试、lint、Debug 构建、API 36（Android 16）模拟器数据库/迁移/UI 测试，并实际 force-stop + adb install -r 验证记录与引导状态。API 35 自动化通过不代表 Android 16/One UI 8 实机验证已通过。检查结果见 docs/BUILD_STATUS.md。

下一阶段优先实现 SAF 手动备份导出与恢复：版本化业务 JSON + 完整性校验；含项目、记录、时间段、列名与结算政策；导出数据库一致快照，恢复前校验、备份当前数据并在事务内替换，失败回退；不直接复制活动 WAL 主库，不申请全盘存储权限。缺少备份时明确提示使用风险。

### 本轮已验证结果

Actions 38502595051：5 个 JVM 测试、11 个 Android 16 模拟器设备测试通过；数据库重开、v1→v2 迁移、事务回滚、价格快照、关键词、重复提交幂等、引导和两倍字体均已执行。另执行 seed 和 verify 各 1 个测试，真实关闭进程并 adb install -r 后，50.00 元记录与引导完成标记仍存在。具体记录见 BUILD_STATUS。

Debug 私钥不进入 Git；当前缓存路径问题导致成功包私钥未保留，签名更新保障尚待解决。`.github/debug-signing-cert.sha256` 只保存公开证书指纹；缓存或指纹不匹配则构建失败。保留缓存不是永久密钥备份，长期发行仍需安全签名配置。

## 0.3 集成功能与旧数据迁移

本轮加入多时间段、跨午夜、自动分钟/小时与手动覆盖、逐日复制、批量模板、项目/日期结算筛选、XLSX/PDF/PNG、可操作的SAF备份恢复和系统分享。保留蓝色中文大字体UI、项目、搜索、新手引导、离线Room。小时数量及新单价最多两位；旧历史单价不自动改写。

**旧0.2已有真实记录，不能直接覆盖升级。** 原成功包私钥未保存；新版包名 `com.easyledger.app.recovery`，名称“简单记账助手·新版”，用于保留旧APP的共存迁移。请先按 [安全迁移说明](docs/SAFE_MIGRATION.md) 实际取得并校验旧数据，不卸载、不清除、不绕过手机保护。新备份功能不代表旧版能自动导出。

设置→备份与恢复，选择文件位置保存JSON，定期复制另一安全位置；恢复先预览有效条数和金额，校验校验和、外键、逐行金额及工时，冲突拒绝、不覆盖。备份明文，请私人保存；卸载/清除或丢失手机仍会丢失未备份数据。结算导出在首页，PNG为逐页图片ZIP；Excel超长单元格会提示而不静默裁剪，建议PDF/PNG。

新固定私钥不入Git、不公开，存于私有目录及仓库Actions Secrets：`EASY_LEDGER_SIGNING_KEY_BASE64`、`EASY_LEDGER_SIGNING_PASSWORD`。缺Secret或证书不匹配不分发临时签名APK。原公开证书和新版公开证书分别保存；密钥缓存不再作为长期保障。两者不能互换。

验证命令（本地Android16设备/模拟器另需实际连接）：

```bash
python3 -m unittest discover -s scripts/tests -v
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.easyledger.app.UpdatePersistenceTest,com.easyledger.app.SafetyMigrationTest
```

云本地构建用 `python3 scripts/cloud-build.py --stable-signing ...` 安全加载外部私钥；不要把密码写命令或日志。Actions集中验证Room/时间/备份/导出/UI/API36，并在纯模拟器测试原0.2取数共存导入和同签名覆盖安装。测试仅合成数据，真实手机不得运行模拟器fixture脚本。新增三星日期时间、微信分享和迁移须实机验收，不因模拟器通过而声称已在三星验证。
