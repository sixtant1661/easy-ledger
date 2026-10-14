# 旧版有记录时的安全迁移

当前原0.2包 com.easyledger.app 的原签名私钥未保存。新的签名不能覆盖它；新增备份按钮也无法自动让已安装旧版获得备份能力。请保留旧APP及其数据，不卸载、不清除，不用新包尝试覆盖。

## 先验证旧版可读

需要电脑、USB数据线、Google Android platform-tools（adb）及Python3。Windows运行 `py`，Mac/Linux运行 `python3`。脚本只用 Python 标准库。无需Android Studio、Root、解锁或编译APP。

三星手机可在设置→关于手机→软件信息点“编译编号”开启开发者选项，再开启USB调试，连接你自己的可信电脑并在手机允许调试授权。企业策略或One UI安全设置可能禁止调试；若无法授权或run-as返回拒绝，立即停止并告诉开发者，不能假定已能读取，也不绕过保护。

运行 `adb devices` 应只出现一台已授权设备（device）；若 unauthorized，先处理手机授权。多设备用脚本 --serial 明确选择。工具对旧APP先实际运行run-as，再停止旧进程并同时读取DB/WAL/SHM。停止进程不删除记录，之后可正常打开旧APP。

Windows示例（将脚本和platform-tools放在电脑自己的目录，替换路径）：

```powershell
py C:\EasyLedgerTools\rescue-old-ledger.py --adb C:\platform-tools\adb.exe --output C:\EasyLedgerPrivate\backup-20261014
```

Mac/Linux示例：

```bash
python3 ./rescue-old-ledger.py --adb ./platform-tools/adb --output ~/EasyLedgerPrivate/backup-20261014
```

输出目录必须不存在，且不在Git仓库。失败不会尝试Root、卸载、清除或写手机数据库；不要凭失败去重装。输出成功包含 old-ledger-private.tar、easy-ledger-backup.json、checksums.json；保留原tar和JSON各两份，放在个人安全位置，不发公开仓库/群聊。备份未加密。

## 取得并校验后，再安装共存新版

仅在电脑脚本成功、已保留双份备份后，才能把JSON复制到手机“下载”等自己能找到的位置，并安装新版 com.easyledger.app.recovery（启动名称“简单记账助手·新版”）。它与旧APP共存，不获取旧版私有目录、不替换旧版。

新版→设置→备份与恢复→选择JSON→核对有效记录数和合计→确认恢复。恢复会校验逐条金额、关系及SHA256；冲突拒绝整次恢复，不覆盖已有记录。不要先在新版输入重复业务数据。

打开历史记录核对项目、日期、数量、历史单价、金额、备注；关闭重开再核对合计。确认一致后在新版保存一份新备份并复制电脑。旧APP继续保留，整个过程不需要卸载它。

## 可证明与不可证明

自动化模拟器测试验证同源码旧Debug版run-as取数及WAL恢复，不能证明你的三星手机已授权访问。当前没有已验证的无需电脑的旧版取数办法。如果三星拒绝run-as或目前无电脑，应继续用旧APP并保留数据，暂缓迁移。

固定的新签名只适用于 com.easyledger.app.recovery 后续升级。未来升级需要同包名、同私钥及非破坏性数据库迁移；签名成功不等于业务数据备份。正常升级不应卸载应用。
