package com.easyledger.app.feature.tools

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.easyledger.app.LedgerApplication
import com.easyledger.app.data.backup.*
import com.easyledger.app.data.export.*
import com.easyledger.app.domain.settlement.*
import com.easyledger.app.feature.ledger.*
import com.easyledger.app.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ToolsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, backupOnly: Boolean = false) {
    val context = LocalContext.current
    val repo = (context.applicationContext as LedgerApplication).repository
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("备份包含业务记录，请保存在自己的安全位置，不要公开发送。") }
    var data by remember { mutableStateOf<LedgerBackup?>(null) }
    var incoming by remember { mutableStateOf<LedgerBackup?>(null) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var project by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var choice by remember { mutableStateOf(false) }
    var document by remember { mutableStateOf<Settlement?>(null) }
    var pendingKind by remember { mutableStateOf("") }
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var lastSharedFile by remember { mutableStateOf<File?>(null) }
    var sharedImages by remember { mutableStateOf(emptyList<File>()) }
    var lastMime by remember { mutableStateOf("") }
    var columns by remember { mutableStateOf(defaultColumnNames) }
    var editingColumns by remember { mutableStateOf(false) }
    fun task(operation: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try { message = withContext(Dispatchers.IO) { operation() } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = if (e is IllegalArgumentException) e.message ?: "操作失败，原数据未清除" else "文件或数据库操作失败，原数据未清除。请检查文件与剩余空间。" }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        try {
            data = withContext(Dispatchers.IO) { repo.backup() }
            val saved = data?.preferences?.firstOrNull { it.key == "settlement_columns" }?.value
            if (saved != null) columns = org.json.JSONArray(saved).let { a -> (0 until a.length()).map { a.getString(it) } }.takeIf { it.size == 8 } ?: defaultColumnNames
        } catch (_: Exception) { message = "读取失败，未清除数据；请勿卸载应用。" }
    }
    fun mime(kind: String) = when(kind) { "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; "pdf" -> "application/pdf"; "png" -> "application/zip"; else -> "application/json" }
    fun suffix(kind: String) = if (kind == "png") "zip" else if (kind == "backup") "json" else kind
    val save = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val kind = pendingKind; val snapshot = document; val backup = pendingBackup
            task {
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(dir, "easy-ledger-${java.util.UUID.randomUUID()}.${suffix(kind)}")
                try {
                    file.outputStream().use { output ->
                        when(kind) { "backup" -> output.write(requireNotNull(backup).toByteArray(Charsets.UTF_8)); "xlsx" -> XlsxExporter.write(requireNotNull(snapshot), output); "pdf" -> PageExporter.pdf(requireNotNull(snapshot), output); "png" -> PageExporter.pngZip(requireNotNull(snapshot), output) }
                    }
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入所选位置" }.use { target -> file.inputStream().use { it.copyTo(target) } }
                    val images = if(kind == "png") buildList {
                        java.util.zip.ZipInputStream(file.inputStream()).use { zip ->
                            var entry=zip.nextEntry
                            while(entry != null) {
                                val image=File(dir,"${java.util.UUID.randomUUID()}.png")
                                image.outputStream().use { zip.copyTo(it) };add(image);entry=zip.nextEntry
                            }
                        }
                    } else emptyList()
                    withContext(Dispatchers.Main) { lastSharedFile = file; lastMime = mime(kind); sharedImages = images }
                    if(kind == "png") "已保存 PNG 图片 ZIP，请解压查看各页。" else "文件已保存。备份请额外复制一份到其他安全位置。"
                } catch (e: Exception) { file.delete(); throw e }
            }
        } else message = "已取消文件保存，记账数据未改变。"
    }
    fun launchSave(kind: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val latest = withContext(Dispatchers.IO) { repo.backup() }
                if (kind == "backup") pendingBackup = withContext(Dispatchers.IO) { BackupCodec.encode(latest) }
                else {
                    require(start.isBlank() || end.isBlank() || start <= end) { "结束日期不能早于开始日期" }
                    document = Settlements.create(latest, project.ifBlank { null }, start.ifBlank { null }, end.ifBlank { null }, columns)
                }
                pendingKind = kind
                save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = mime(kind); putExtra(Intent.EXTRA_TITLE, "easy-ledger-${java.time.LocalDate.now()}.${suffix(kind)}") })
            } catch (e: Exception) { message = e.message ?: "准备文件失败，原数据未改变" }
            finally { busy = false }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) task {
            val text = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开备份" }.use { stream ->
                val bytes = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = stream.read(buffer); if(count < 0) break; require(bytes.size()+count <= BackupCodec.MAX_BYTES) { "备份超过32MB" }; bytes.write(buffer,0,count) }
                bytes.toString("UTF-8")
            }
            val decoded = BackupCodec.decode(text)
            withContext(Dispatchers.Main) { incoming = decoded; restoreConfirm = true }
            "备份完整性和金额已校验，请核对后确认恢复。"
        }
    }
    val preview = data?.let { runCatching { Settlements.create(it, project.ifBlank { null }, start.ifBlank { null }, end.ifBlank { null }, columns) }.getOrNull() }
    PageContent(if(backupOnly) "备份与恢复" else "结算查询与导出", modifier) {
        if (!backupOnly) {
            Box {
                OutlinedButton({ choice = true }, Modifier.fillMaxWidth().heightIn(min=56.dp), enabled=!busy) { Text("项目：${data?.projects?.firstOrNull { it.id == project }?.name ?: "全部项目"} ▾") }
                DropdownMenu(choice, { choice = false }) {
                    DropdownMenuItem({ Text("全部项目") }, { project=""; choice=false })
                    data?.projects?.forEach { p -> DropdownMenuItem({ Text(p.name) }, { project=p.id; choice=false }) }
                }
            }
            DateButton("开始日期", start.ifBlank { java.time.LocalDate.now().toString() }) { start = it }
            DateButton("结束日期", end.ifBlank { java.time.LocalDate.now().toString() }) { end = it }
            TextButton({ start=""; end="" }, Modifier.heightIn(min=56.dp)) { Text("清除日期筛选（全部日期）") }
            if (preview != null) InformationCard("结算合计", "${preview.rows.size} 条 · ${preview.total} 元\n${preview.period}")
            MainButton("修改结算列名", !busy) { editingColumns = !editingColumns }
            if (editingColumns) {
                columns.forEachIndexed { i, value -> TextField("第 ${i+1} 列（${defaultColumnNames[i]}）", value, { next -> columns = columns.toMutableList().also { it[i]=next } }) }
                MainButton("保存列名", !busy) { task {
                    require(columns.size == 8 && columns.all { it.isNotBlank() && it.length <= 24 }) { "列名须填写完整，每列最多24字" }
                    repo.dao.preference(com.easyledger.app.data.local.PreferenceEntity("settlement_columns", org.json.JSONArray(columns).toString()))
                    "结算列名已保存；不会修改金额或数据库含义。"
                } }
            }
            MainButton("导出 Excel（XLSX）", !busy) { launchSave("xlsx") }
            MainButton("导出 PDF", !busy) { launchSave("pdf") }
            MainButton("导出 PNG 图片（ZIP）", !busy) { launchSave("png") }
            preview?.rows?.forEach { row -> InformationCard("${row.cells[0]} · ${row.cells[1]}", row.cells.mapIndexed { i,v -> "${columns[i]}：${v.ifBlank { "无" }}" }.joinToString("\n")) }
        }
        MainButton("保存本地备份", !busy) { launchSave("backup") }
        MainButton("选择备份并恢复", !busy) { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        Text("恢复会保留现有记录；同一记录有冲突时整次拒绝恢复，不覆盖、不清库。备份未加密，请保存在私人位置。")
        InformationCard(if(busy) "正在处理，请稍候" else "操作提示", message)
        if (lastSharedFile != null) MainButton("分享刚保存的文件", !busy) {
            try {
                val uris=(sharedImages.ifEmpty { listOf(lastSharedFile!!) }).map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
                val send=Intent(if(uris.size>1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                    type=if(sharedImages.isEmpty()) lastMime else "image/png"
                    if(uris.size>1) putParcelableArrayListExtra(Intent.EXTRA_STREAM,java.util.ArrayList(uris)) else putExtra(Intent.EXTRA_STREAM,uris.first())
                    clipData=android.content.ClipData.newRawUri("结算文件",uris.first()).apply { uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "选择微信或其他应用"))
            } catch (_: Exception) { message="没有可用的分享应用；保存的文件仍可在“我的文件”中找到。" }
        }
        OutlinedButton(onBack, Modifier.fillMaxWidth().heightIn(min=56.dp), enabled=!busy) { Text("返回") }
    }
    if(restoreConfirm) AlertDialog(onDismissRequest={ if(!busy) restoreConfirm=false }, title={ Text("确认恢复备份？") }, text={ Text("${incoming?.projects?.size ?: 0} 个项目\n${incoming?.activeRecords?.size ?: 0} 条有效记录\n合计 ${incoming?.total ?: ""} 元\n已校验文件与金额。现有历史不覆盖；有冲突时拒绝整次恢复。") },
        confirmButton={ TextButton({ val snapshot=incoming ?: return@TextButton; restoreConfirm=false; task { repo.restore(snapshot); val latest=repo.backup(); withContext(Dispatchers.Main) { data=latest }; "恢复成功，记录和金额已核对。请查看历史记录。" } }, enabled=!busy) { Text("确认恢复") } }, dismissButton={ TextButton({ restoreConfirm=false }, enabled=!busy) { Text("取消") } })
}
