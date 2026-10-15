# Easy Ledger 技术架构草案

## 1. 当前环境与选型

初次检查时仓库为空且远端无分支，环境仅提供缺少 javac 的 JRE 21。本里程碑在 feature/android-foundation 分支创建基础 Android 工程，补充完整 Temurin JDK 17.0.18+8、SDK Platform 35、Build Tools 35.0.0 与 Gradle Wrapper。原 main 未修改。实际构建、测试及尚未完成的三星设备验证见 BUILD_STATUS.md。

选用 Kotlin、Compose、Material 3、MVVM、Room、StateFlow、Coroutines、Navigation Compose、Gradle Kotlin DSL 和 JUnit。基础工程版本已固定于 gradle/libs.versions.toml，验证结果见 BUILD_STATUS.md。初始化时查官方兼容矩阵，固定 AGP、Gradle Wrapper、JDK、Kotlin/Compose 编译器插件、KSP、Room 和 AndroidX 版本，使用版本目录，不采用动态版本。Kotlin 2.x 时 Compose 编译器插件跟随 Kotlin 兼容要求，不凭名称混搭。

本里程碑 minSdk 23 仅为暂定技术构建下限，最低产品支持版本未确认，必须核实妈妈的三星手机型号、Android 与 One UI 版本后再决策。后续使用 java.time 时须评估低版本核心库脱糖。compileSdk/targetSdk 选择实施时已发布稳定且符合分发要求的版本，记录版本选择理由。JDK 21 已存在不代表未来选定的 AGP/Gradle 可以直接使用；必要时配置受支持 JDK。当前已授权基础工程开发，工具链安装及验证结果另见 BUILD_STATUS.md。

## 2. 目录与依赖方向

第一阶段使用单 app 模块、按职责分包，避免过早拆分模块。以下是规划路径，不代表文件已经创建。

```text
easy-ledger/
├── AGENTS.md
├── docs/PRD.md、ARCHITECTURE.md、ROADMAP.md
├── settings.gradle.kts / build.gradle.kts
├── gradle/libs.versions.toml / wrapper/
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   └── java/com/easyledger/app/
        │       ├── EasyLedgerApplication.kt
        │       ├── MainActivity.kt
        │       ├── core/{decimal,time,validation}/
        │       ├── domain/{model,repository,usecase}/
        │       ├── data/local/{entity,dao,converter,migration}/
        │       ├── data/repository/
        │       ├── ui/{navigation,theme,components}/
        │       ├── feature/{home,projects,settings,projectdetail,recordeditor,recorddetail,projectsettings}/
        │       └── settlement/{model,assembler}/  # 后续再增加渲染器、导出器
        ├── test/  # JVM 领域计算与 ViewModel 测试
        └── androidTest/  # Room、Compose、设备功能测试
```

UI → ViewModel → 用例/仓库接口 → 仓库实现 → DAO。domain 和 core 计算不依赖 Android UI；领域模型不直接使用 Room 实体。采用应用级依赖容器即可，暂不强制引入 DI 框架。

ViewModel 暴露不可变 StateFlow；Compose 按生命周期收集。写操作由协程和事务执行，UI 保存态防止重复提交，日期时间通过可替换 Clock 提供以便测试。导航传稳定 ID，不传整份可变实体。编辑未保存状态支持配置变化和进程重建，不能静默当作已保存。

## 3. 数据模型与关系

所有 ID 推荐 UUID 字符串以便未来备份合并。审计时刻用 UTC epoch milliseconds，工作日期用 ISO LocalDate 或 epochDay，时间段保存完整 ISO LocalDateTime 起止值，不能把业务日期当作 UTC 午夜换算。

| 实体 | 主要字段 | 说明 |
| --- | --- | --- |
| Project | id、name、defaultBusinessName、defaultBillingMode、defaultUnit、defaultUnitPriceText、currencyCode、createdAt、updatedAt、archivedAt | 项目默认值；货币范围待确认 |
| ProjectColumnLabel | projectId、columnKey、displayLabel | 联合主键；缺失时使用标准中文名称 |
| WorkRecord | id、projectId、workDate、businessNameSnapshot、billingModeSnapshot、unitSnapshot、unitPriceText、actualMinutes、billingQuantityText、quantitySource、amountText、calculationPolicyVersion、note、createdAt、updatedAt、deletedAt | quantitySource 为 AUTO/MANUAL；自动仅用于已定义的单位换算；金额为受控计算缓存 |
| WorkSegment | id、recordId、position、startDateTimeText、endDateTimeText | 按 position 排序；删除时间段不删除记录 |
| SettlementSnapshot（后续） | id、projectId、schemaVersion、createdAt、rangeStart、rangeEnd、currencyCode、totalText、documentPayload | 不可变序列化结算文档，包含显示列名、项目标题、源记录内容和计算政策 |
| SettlementSnapshotRecord（后续） | snapshotId、sourceRecordId | 来源追踪；快照真实内容不依赖活记录 |

关系：Project 1:N WorkRecord；WorkRecord 1:N WorkSegment；Project 1:N ProjectColumnLabel；Project 1:N SettlementSnapshot；Snapshot 与源记录通过关联表追踪。

Project 外键引用采用 RESTRICT，不允许删除项目导致历史记录消失。记录软删除，正常查询排除 deletedAt。时间段替换可在单次事务内删除旧段并插入新段；应用不提供绕过记录用例的独立写接口。快照来源关联不得通过级联删除破坏快照，后续清理策略确定后再设计外键行为。

推荐索引：WorkRecord(projectId, workDate, deletedAt)，WorkSegment(recordId, position) 唯一索引；ProjectColumnLabel(projectId, columnKey) 唯一。不要创建 projectId + workDate 唯一约束，因同日多条规则未禁止。

稳定列键为 DATE、BUSINESS、WORK_TIME、QUANTITY、UNIT、UNIT_PRICE、AMOUNT、NOTE；标题、项目名称、合计、签字是表格结构元素。改显示名称不能重命名列键、改变公式或产生行业专属表。

## 4. 时间与金额算法

### 时间段

时间选择器产生时分；用户明确结束日期偏移（当天或次日）。构造并保存开始/结束 LocalDateTime，用 epochDay×1440 + 时×60+分 转换为墙钟分钟坐标用于计算；偏移仅为编辑状态，不能代替完整日期时间持久化。每段区间为 [start, end)，必须 end > start；跨度限制和相等时处理以 PRD 待确认规则为准。排序后若 current.start < previous.end 则重叠，等于允许；累计 end-start 得到实际整数分钟。

必须将工作日期加偏移转换到同一绝对日分钟坐标，才能识别上一条跨午夜段与下一天记录的交叉。手机时区变化不改写已有工作日期。本方案按墙上时钟差计算，若需处理夏令时真实 elapsed time，必须另行确定业务政策并增加 ZoneId/Instant 信息；不能无意引入时区偏移导致工时变化。

### 十进制

SQLite REAL、Float/Double 和 SQL SUM(text) 都不用于业务金额。Room 转换器将 BigDecimal 以规范 plain string 保存，从十进制文本构造；实际分钟保留整数。总额在 Kotlin 中对明确舍入后的记录金额做 BigDecimal 累加。

“分钟 → 小时”可能得到无限小数，必须显式使用已确认的精度和 RoundingMode。乘法先精确计算，再按已确认金额政策舍入；政策版本随记录保存。数量来源、原始实际时长和计费数量独立，不从显示字符串反推事实。金额缓存只能由领域计算器生成；读取异常值时给出明确诊断，不静默重写历史。

项目默认参数变化不追溯历史是推荐方案，确认后作为快照规则实现。切换计费方式或修改时间段时，手动数量是否保留必须按确定的政策提示用户，不静默覆盖。

## 5. Room 安全与一致性

- 保存记录、替换全部时间段及更新派生字段放在同一 withTransaction 中，失败全部回滚。
- 删除为软删除，汇总排除已删除记录；项目汇总从记录重算，第一阶段不维护第二套总额缓存。
- DAO 提供 Flow 供界面更新，不在主线程读写；更新条件防止无意写入已归档/已删除对象，归档可否继续编辑历史需明确 UI 政策。
- 开启 schema 导出并提交无业务数据的 schema JSON；升级采用显式 Migration，禁止正式数据使用 destructive migration。
- 初版私有存储不等于已加密；日志不输出业务内容。应明确 Android 系统备份配置，不能默认将系统云备份视为离线备份方案。
- 后续备份使用一致性快照，不能在 Room/WAL 写入过程中只拷贝主数据库文件；恢复验证版本、校验、实体关系，暂存验证后再替换，失败可回退。

## 6. 页面与导航

| 页面 | 内容和操作 | 导航 |
| --- | --- | --- |
| 项目列表 | 大字号项目卡片、汇总、归档筛选、新建 | 起点 → 项目详情/新建 |
| 新建/编辑项目 | 名称、业务默认值、计费方式、单位、单价 | 保存回列表或详情 |
| 项目详情 | 日期排序记录、总额、当天新增、归档、项目设置 | → 记录详情/编辑器/设置 |
| 记录编辑器 | 快捷日期、日历、动态时间段、实际分钟、数量来源、计费数量、单价、金额、备注 | 新增或编辑；保存返回详情 |
| 记录详情 | 完整核对内容、手动标识、编辑、复制、删除确认 | → 编辑器；复制重新选日期 |
| 项目设置 | 默认值编辑、统一列名配置 | 返回项目详情 |
| 结算预览/历史（后续） | 范围、快照、预览、导出分享 | 后续阶段新增 |

当前基础工程采用首页/项目/设置三项底部导航；后续项目详情和记录编辑采用项目内导航。表单使用可滚动竖排结构，时间段单独卡片；常用保存按钮显著，键盘和大字体不得遮挡关键操作。时间段删除与记录删除有不同文案，金额总览保持清晰。使用稳定 route 参数；返回时有未保存内容则提示。

## 7. 后续导出边界

SettlementDocument 是独立于 UI、Room 和具体文件格式的不可变结构；assembler 从选中记录生成，渲染器只负责布局。ExcelWriter、PdfRenderer、PngRenderer 与 Compose 预览共用文档，不分别计算金额。

Excel 库实施前评估 Android 兼容性、包体、内存、许可证及样式能力，禁止因 JVM 可用而假定 Android 可用。大表格需要限制图片尺寸、分页或分块，不在主线程生成。使用 Storage Access Framework 保存、FileProvider 授予分享临时读取权限，不申请全盘访问。

## 8. 验证分层与关键风险

JVM：区间合计、跨午夜、嵌套重叠、相接、非法区间、手动数量状态、精度舍入、项目汇总。Room 设备测试：实体关系、多表事务、更新、软删除和历史默认值快照，后续增加迁移测试。Compose/实机：日历及时间选择、字体缩放、长文本、输入恢复、旋转、重复保存、删除确认。APK 能构建不代表这些检查通过。

主要风险：未确认舍入规则造成结算差额；跨日重叠坐标处理错误；修改项目默认值污染历史；Room 多表更新部分成功；大字体导致按钮或金额截断；不同导出器内容漂移；备份损坏或恢复重复；Android 工具链不兼容。控制措施分别为显式政策、可测纯函数、记录快照、事务、设备验收、统一文档、校验/回退和固定版本兼容验证。

## 本轮暂定业务政策（待产品方最终确认）

以下由产品方授权作为暂定默认方案。状态统一为「待产品方最终确认」，不是最终结算合同；本里程碑仅记录政策，不实现结算功能。

1. 单条金额以精确十进制计算，按 HALF_UP 保留两位小数；合计为各行已舍入金额之和。
2. 实际工时按整数分钟计算；计费小时可手动调整，最多两位小数。分钟自动转换为小时的取整方式仍待最终确认，推荐 HALF_UP；例如 1 分钟可显示为 0.02 小时，但实际工时仍保存 1 分钟。
3. 按天计费默认手动填写天数，不从实际工时自动换算。
4. 修改计费数量保留原始工作时间段和实际工时，重新计算金额；修改时间段后如何处理已有手动数量仍按待确认政策提示。
5. 跨午夜工作归属开始日期，保存完整起止日期时间。每段均存起止 LocalDateTime；首版建议同一记录各段从工作日期开始，后续日期段输入及超 24 小时限制仍需确认。墙钟工时与夏令时真实经过时长的选择仍待确认。
6. 同一天允许多条记录；疑似重复提醒，不强制禁止。同条记录内的时间段重叠仍阻止，首尾相接允许。
7. 每条记录独立保存当时单价，项目默认单价修改不追溯历史记录。
8. 结算单工作时间上下排列，每段一行，按时间段数量、换行文本及字体自动计算行高，不用空格排版。

### 三星设备验证约束

妈妈使用三星 Android 手机，型号、Android 和 One UI 版本待确认。不能将暂定 minSdk 当成已获认可的最低支持版本。本次只提供首页、项目占位与设置；后续日期/时间选择、最大字体及屏幕缩放、手势/三键导航、文件选择器、Excel 打开与微信分享都需在目标三星设备测试。不得因使用系统或 Material 组件就声称 One UI 兼容通过。

## 0.2 本地工作记账里程碑

目标设备已确认 Galaxy S21 FE / Android 16 / One UI 8.0，基础版已有用户实机验收。本轮保留原风格和导航，增加 Room 本地项目/记录、手动数量计费、日历和快捷日期、历史详情、关键词搜索与首次三步引导。工时段输入、批量补录、导出及备份恢复未实现。

用户已确认数量大于 0、单价允许 0、拒绝负数冲销、小时数量最多两位小数；按天手填。金额继续精确十进制 HALF_UP 两位并合计舍入后各行金额。记录单价独立快照，不受项目默认单价修改影响。

实际 Room v2 模型见 data/local：Project、WorkRecord、WorkSegment（未来扩展）、Preference。项目备注用于搜索，v1→v2 显式迁移增加备注列；v1 为测试基线，未向用户发布。项目/记录软删除，项目删除保留记录；数量改变不删除原时间段。项目列名和结算快照目前仍是后续设计，未声明实现。

首次引导完成/跳过持久化于 Room，设置入口可重看。搜索使用本地 SQL 字符串包含查询，百分号/下划线按字面关键词处理，不作为通配符。金额不使用 SQLite REAL 或 SUM(text)。多表写入事务化，不启用破坏性迁移。

验收必须包含新增/编辑/删除、价格快照、金额/舍入、搜索、事务回滚、数据库文件重开、v1→v2 迁移、引导和 UI、同签名覆盖安装的数据保留。测试结果按实际执行记录至 BUILD_STATUS。API 36（Android 16）模拟器不能代替三星 Android 16 真机验收。

下一优先项是可导出、校验、可回退的本地备份恢复，使用 SAF，不访问服务器。不将手机数据库、签名私钥或真实记录提交 Git。签名缓存失效拒绝更新包，固定发布密钥必须另行安全配置；卸载/清除数据会丢失记录。

## 0.3 数据保护、时间、导出与签名

保留 Room schema version2 和既有四表，不改变任何历史字段/数据库名称；工时写入已预留 work_segments 及 actualMinutes/quantitySource。版本2升级到新业务层不需要清表；1→2仍显式迁移。记录+时段、整批、备份快照和恢复均 withTransaction。单价修改不追溯；新建价格两位，已有更高精度价格仅在值未变时保留历史计费依据。

WorkTime 是纯 Kotlin 整分钟与 LocalDateTime 校验、跨日移动、分钟转小时和连续日期生成。Java time 通过 desugar_jdk_libs2.1.5 支持既有暂定 minSdk23。RecordEditor 的保存型状态区分原始时间段、自动/手动计费，日期修改移动全部段并保留跨日偏移。批量 ID+日期用于重试幂等，不靠同日禁止重复。

LedgerBackup 为完整实体快照；BackupCodec 使用版本化 JSON envelope（payload字符串 + SHA256），最大32MB，先校验后事务无冲突追加。SHA256只检查完整性，不能防有意篡改。保留原始精度、主键和时间戳；冲突不做隐式 last-write-wins。SAF 读写由 IO 执行，用户确认恢复；外部存储无需全盘权限。分享仅 exports 缓存子目录的 FileProvider content URI，临时读权限，不暴露文件路径。

Settlements.create 只过滤已有有效记录并累计行金额，生成不可变列名/单元格/总额快照。XlsxExporter 最小标准OOXML Zip：inlineStr 防备注变公式，显式行高/宽/边框/居中/wrap/打印。PageExporter 共用测量换行/分页/Canvas，在PDF与PNG输出同样单元格，不再算金额。大量结算与复杂原始Excel样式仍须实际样表验收。

原成功0.2签名指纹仍留 .github/debug-signing-cert.sha256；现存本地密钥与该指纹不符、缓存无原私钥，不可覆盖原 com.easyledger.app。新版另用 com.easyledger.app.recovery 和 .github/recovery-signing-cert.sha256；私钥外部私有目录 + GitHub Actions Secrets（base64加密keystore、独立密码），不用会过期的cache作为唯一存储。CI可以运行临时签名测试，但缺持久密钥/指纹不匹配禁止分发APK。不得把新版密钥误认为旧版密钥。

scripts/rescue-old-ledger.py：设备 get-state/run-as 成功→force-stop（不清数据）→exec-out tar 同时读取DB/WAL/SHM→电脑临时副本 SQLite 完整性/外键/计费校验→通用JSON。禁止归档路径穿越/链接，输出不覆盖目录、不写Git。verify-legacy-rescue.py 在合成模拟器构建原0.2源码、验证真实run-as取数、共存导入及旧DB哈希不变。实机访问权限须重新验证，不启用Root或任何绕过。

### CI职责拆分（最新授权）

主 android-debug-apk.yml 仅构建当前应用、必要JVM/Lint/API36设备测试、同签名保留检查、固定指纹校验与APK产物；另一个新runner只重复构建验证固定签名一致性。legacy-data-rescue.yml 单独手工触发旧源码/WAL救援验证，保留严格SHA及关键字段断言；当前原始文件SHA断言失败，尚不能作为真实救援证明。此调整不修改数据库迁移或任何记账业务代码。


最新个人测试交付授权：主 APK 工作流仅执行 assembleDebug、固定证书指纹校验和 Artifact 上传；本轮不运行模拟器、Lint、单元测试或重复构建。保留全部业务、迁移代码和安全断言，旧版救援工作流仅手动运行且本轮不触发。此 APK 供产品方三星实机测试，不代表完整发布验收。独立包名保持不变，不覆盖旧版。
