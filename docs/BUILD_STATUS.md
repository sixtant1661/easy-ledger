# 基础工程实际验证记录

## 本次环境与执行

分支：`feature/android-foundation`。原仓库无提交或 main 分支，本次仅在独立功能分支开发，未创建或修改 main，未推送或发布。

原机器仅有缺少 javac 的 JRE 21；现已安装完整 Temurin JDK 17.0.18+8、Android Command-line Tools 19.0、SDK Platform 35、Build Tools 35.0.0、Platform Tools，位于仓库外。Gradle 8.11.1 Wrapper 的分发 SHA-256 与 Wrapper JAR 官方校验均已验证。JDK 下载按官方 SHA-256 验证，SDK 命令行工具按 Google 仓库清单校验；未禁用 TLS。

初始失败分别由 Java 未使用云代理、Android 用户目录不可写和原 JRE 缺少编译器造成。通过受支持的代理/信任库、ANDROID_USER_HOME、仓库外缓存及完整 JDK 修复。安装脚本和构建入口保存在 scripts/，本地步骤见 README。`setup-cloud.py` 已在已安装环境上实际运行验证；固定工具链的首次下载和安装步骤也已分别执行，但未在清空整个工具目录后重复重装。

实际最终命令：

```sh
python3 scripts/cloud-build.py
# 运行：testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

## 最终结果

| 检查 | 结果 |
| --- | --- |
| Gradle 完整任务组 | BUILD SUCCESSFUL，退出码 0 |
| JVM HomeViewModelTest | 2 个测试，0 失败、0 错误、0 跳过 |
| lintDebug | 0 个 issue；早期备份规则警告已修复 |
| assembleDebug | 真实生成 Debug APK |
| assembleDebugAndroidTest | 真实生成测试 APK，含导航/说明及两倍字体测试 |
| APK 签名校验 | apksigner 退出码 0；v1/v2 验证通过 |
| APK 权限检查 | 无 INTERNET、广泛存储或危险权限；含 AndroidX 自动生成的私有动态接收器签名权限 |
| XML 结构和文档围栏 | 检查通过，不能替代业务测试 |
| connectedDebugAndroidTest | 未执行，无可用设备/模拟器目标 |
| 真机安装、启动、One UI 与 TalkBack | 未执行 |

Gradle/SDK 的旧 metrics 组件仍报告默认 /home/agent/.android 不可写的非阻断提示；构建成功，应用未集成采集 SDK。adb 尝试读取设备时也因该默认目录不可写退出，未据此声称设备列表为空；云环境未准备可用设备目标。设备测试必须在可用手机/模拟器环境另行运行。

apksigner 同时输出针对 v1 JAR META-INF 元数据的警告；APK 的 v2 签名验证通过。此产物为 Debug 签名预览版，不是正式发布包。

## APK

- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 大小：10959132 字节
- SHA-256：`5db5675b51ce56e6feedb3147980249ade008ee9c809845cc376dabc27b99b16`
- 测试包：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- JVM 报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- Lint 报告：`app/build/reports/lint-results-debug.html`

以上文件真实存在，但构建产物被 Git 忽略。已保存云环境 install_script 与 start_skill 草稿，不代表环境已经发布，也未在新任务中验证快照恢复。发布需在环境设置中检查并保存配置，再发布环境。

## 验收范围和暂停点

八条规则已作为“待产品方最终确认”的暂定政策补充到 PRD/ARCHITECTURE，本版本未实现计算或数据库。首页、项目占位和设置采用 Corporate Clean、大字号、至少 56dp 主按钮与 Material 点击反馈。大字体采用响应式滚动布局，设备测试代码已编译，但不能声称三星适老化实机检查通过。

minSdk 23 为暂定技术构建下限；三星型号、Android/One UI 与产品最低支持版本仍待确认。日期/时间选择、文件导出和微信分享不在此版本中，需要未来阶段在目标三星手机验证。

本里程碑到此暂停，等待产品方验收。建议先安装 Debug APK 检查基础页面和最大字体，确认设备型号/系统，然后授权下一里程碑的领域计算、项目管理和 Room 工作。
