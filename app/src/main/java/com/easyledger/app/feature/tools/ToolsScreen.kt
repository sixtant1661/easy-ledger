package com.easyledger.app.feature.tools

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.easyledger.app.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.List
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
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
import java.time.LocalDate
import java.time.YearMonth

private fun safeName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').take(80).ifBlank { "工作结算单" }

@Composable
fun ToolsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, backupOnly: Boolean = false, onBusyChange: (Boolean) -> Unit = {}, historyOnly: Boolean = false, onRecord: (String) -> Unit = {}) {
    val context = LocalContext.current
    val repo = (context.applicationContext as LedgerApplication).repository
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<LedgerBackup?>(null) }
    var incoming by remember { mutableStateOf<LedgerBackup?>(null) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var project by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var rangeStart by rememberSaveable { mutableStateOf(YearMonth.now().atDay(1).toString()) }
    var rangeEnd by rememberSaveable { mutableStateOf(YearMonth.now().atEndOfMonth().toString()) }
    var customRange by rememberSaveable { mutableStateOf(false) }
    val selectedMonth=YearMonth.parse(month)
    val start=if(customRange) rangeStart else selectedMonth.atDay(1).toString()
    val end=if(customRange) rangeEnd else selectedMonth.atEndOfMonth().toString()
    var choosingMonth by remember {mutableStateOf(false)}
    var keyword by rememberSaveable {mutableStateOf("")}
    var choice by remember { mutableStateOf(false) }
    var showDates by rememberSaveable { mutableStateOf(false) }
    var exportKind by rememberSaveable { mutableStateOf("") }
    var fileBase by rememberSaveable { mutableStateOf("") }
    var naming by rememberSaveable { mutableStateOf(false) }
    var ready by rememberSaveable { mutableStateOf(false) }
    var files by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var savePage by rememberSaveable { mutableIntStateOf(0) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var columns by remember { mutableStateOf(defaultColumnNames) }
    var editingColumns by remember { mutableStateOf(false) }
    LaunchedEffect(busy,saving) {onBusyChange(busy || saving)}
    DisposableEffect(Unit) {onDispose {onBusyChange(false)}}
    BackHandler(enabled=busy || saving) { }
    fun mime(kind: String) = when(kind) { "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; "pdf" -> "application/pdf"; "png" -> "image/png"; else -> "application/json" }
    fun task(operation: suspend () -> String) {
        if(busy) return
        busy=true
        scope.launch {
            try { message=withContext(Dispatchers.IO) { operation() } }
            catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { saving=false;message=if(e is IllegalArgumentException) e.message ?: "操作失败" else "文件操作失败，请检查空间和保存位置；记账数据未改变。" }
            finally {busy=false}
        }
    }
    LaunchedEffect(Unit) {
        try {
            data=withContext(Dispatchers.IO) {repo.backup()}
            val saved=data?.preferences?.firstOrNull {it.key=="settlement_columns"}?.value
            if(saved!=null) columns=org.json.JSONArray(saved).let {a -> (0 until a.length()).map {a.getString(it)}}.takeIf {it.size==8} ?: defaultColumnNames
            if(project.isBlank()) project=data?.projects?.firstOrNull {it.deletedAt==null}?.id.orEmpty()
        } catch(_: Exception) {message="读取失败，原数据未清除。"}
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri=result.data?.data
        if(result.resultCode==Activity.RESULT_OK && uri!=null) {
            val path=files.getOrNull(savePage)
            task {
                val file=File(requireNotNull(path) {"待保存文件已失效，请重新导出"})
                val exportRoot=File(context.cacheDir,"exports").canonicalPath+File.separator
                require(file.canonicalPath.startsWith(exportRoot) && file.isFile && file.length()>0) {"待保存文件已失效，请重新导出"}
                requireNotNull(context.contentResolver.openOutputStream(uri,"w")) {"无法写入保存位置"}.use {target -> file.inputStream().use {it.copyTo(target)}}
                withContext(Dispatchers.Main) {
                    if(savePage+1<files.size) savePage++ else {saving=false;ready=false}
                }
                if(files.size>1) "已保存第 ${savePage.coerceAtLeast(1)} 页，请依次保存全部图片。" else "文件已保存。"
            }
        } else {saving=false;message="已取消后续保存，已保存文件和记账数据未改变。"}
    }
    LaunchedEffect(saving,savePage,busy) {
        if(saving && !busy) {
            val file=files.getOrNull(savePage)?.let(::File)
            if(file==null || !file.isFile) {saving=false;message="待保存文件失效，请重新导出"}
            else save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE);type=mime(exportKind);putExtra(Intent.EXTRA_TITLE,file.name)
            })
        }
    }
    fun generate(kind: String) {
        task {
            val latest=repo.backup()
            require(start<=end) {"结束日期不能早于开始日期"}
            if(kind!="backup") require(project.isNotBlank()) {"请先选择项目"}
            val dir=File(context.cacheDir,"exports/${java.util.UUID.randomUUID()}").apply {mkdirs()}
            val base=safeName(fileBase)
            val produced=if(kind=="backup") {
                listOf(File(dir,"$base.json").apply {writeText(BackupCodec.encode(latest))})
            } else {
                val doc=Settlements.create(latest,project,start,end,columns)
                if(kind=="png") PageExporter.pngFiles(doc,dir,base)
                else listOf(File(dir,"$base.$kind").apply {outputStream().use {output -> if(kind=="xlsx") XlsxExporter.write(doc,output) else PageExporter.pdf(doc,output)}})
            }
            require(produced.isNotEmpty() && produced.all {it.length()>0}) {"文件生成失败"}
            withContext(Dispatchers.Main) {files=produced.map {it.absolutePath};exportKind=kind;ready=true;data=latest}
            "文件已生成，请选择保存或分享。"
        }
    }
    fun beginExport(kind: String) {
        exportKind=kind
        fileBase=if(kind=="backup") "易记账备份" else data?.projects?.firstOrNull {it.id==project}?.name.orEmpty()
        naming=true
    }
    fun share() {
        try {
            val uris=files.map {FileProvider.getUriForFile(context,"${context.packageName}.files",File(it))}
            require(uris.isNotEmpty())
            val intent=Intent(if(uris.size>1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                type=mime(exportKind)
                if(uris.size>1) putParcelableArrayListExtra(Intent.EXTRA_STREAM,java.util.ArrayList(uris)) else putExtra(Intent.EXTRA_STREAM,uris.first())
                clipData=android.content.ClipData.newRawUri("账单文件",uris.first()).apply {uris.drop(1).forEach {addItem(android.content.ClipData.Item(it))}}
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent,"分享文件"))
        } catch(_: Exception) {message="无法分享，请选择保存文件。"}
    }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) task {
            val text=requireNotNull(context.contentResolver.openInputStream(uri)).use {stream ->
                val bytes=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {val count=stream.read(buffer);if(count<0) break;require(bytes.size()+count<=BackupCodec.MAX_BYTES) {"备份超过32MB"};bytes.write(buffer,0,count)}
                bytes.toString("UTF-8")
            }
            val decoded=BackupCodec.decode(text)
            withContext(Dispatchers.Main) {incoming=decoded;restoreConfirm=true}
            "备份完整性及金额已校验。"
        }
    }
    val selectedData=remember(data,project,start,end,keyword) {data?.let {backup ->
        val names=backup.projects.associate {it.id to it.name}
        backup.copy(records=backup.records.filter {it.projectId==project && it.date>=start && it.date<=end && (keyword.isBlank() || it.note.contains(keyword,ignoreCase=true) || names[it.projectId].orEmpty().contains(keyword,ignoreCase=true))})
    }}
    val preview=remember(selectedData,project,start,end,columns) {selectedData?.takeIf {project.isNotBlank()}?.let {runCatching {Settlements.create(it,project,start,end,columns)}.getOrNull()}}
    val rowIds=selectedData?.activeRecords.orEmpty().sortedWith(compareBy({it.date},{it.createdAt},{it.id})).map {it.id}
    PageContent(if(backupOnly) "备份与恢复" else if(historyOnly) "历史记录" else if(customRange) "自定义账单" else "${selectedMonth.year}年${selectedMonth.monthValue}月账单",modifier) {
        if(!backupOnly) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    TextButton({choice=true},Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!busy,contentPadding=PaddingValues(horizontal=4.dp)) {Text("${data?.projects?.firstOrNull {it.id==project}?.name ?: "选择项目"} ▾",maxLines=2,overflow=TextOverflow.Ellipsis)}
                    DropdownMenu(choice,{choice=false}) {data?.projects?.forEach {p -> DropdownMenuItem({Text(p.name)},{project=p.id;choice=false})}}
                }
                TextButton({choosingMonth=true},Modifier.heightIn(min=48.dp),enabled=!busy,contentPadding=PaddingValues(horizontal=4.dp)) {Text(if(customRange) "自定义日期 ▾" else "${selectedMonth.year}年${selectedMonth.monthValue}月 ▾",fontSize=16.sp)}
            }
            TextButton({showDates=!showDates},Modifier.heightIn(min=40.dp),contentPadding=PaddingValues(horizontal=4.dp)) {Text("高级日期筛选 ${if(showDates) "⌃" else "⌄"}",fontSize=14.sp)}
            if(showDates) {
                Row {
                    TextButton({month=selectedMonth.minusMonths(1).toString();customRange=false},Modifier.weight(1f)) {Text("‹ 上个月")}
                    TextButton({month=selectedMonth.plusMonths(1).toString();customRange=false},Modifier.weight(1f)) {Text("下个月 ›")}
                }
                DateButton("开始日期",start) {rangeStart=it;if(!customRange)rangeEnd=end;customRange=true}
                DateButton("结束日期",end) {rangeEnd=it;if(!customRange)rangeStart=start;customRange=true}
                if(customRange) TextButton({customRange=false}) {Text("恢复所选月份")}
            }
            if(customRange) Text("$start 至 $end",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(start>end) Text("结束日期不能早于开始日期，请调整日期范围。",fontSize=14.sp,color=MaterialTheme.colorScheme.error)
            if(historyOnly) SearchField(keyword,{keyword=it},"搜索项目名称或备注")
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color(0xFFEAF4F1))) {
                Column(Modifier.padding(16.dp)) {Text("累计金额（元）");Text(preview?.total ?: "0.00",style=MaterialTheme.typography.headlineMedium);Text("共 ${preview?.rows?.size ?: 0} 条记录",style=MaterialTheme.typography.bodyMedium)}
            }
            LedgerTable(listOf("日期","工作时间","时长","单价","金额"),preview?.rows.orEmpty().map {r -> listOf(r.cells[0].substring(5).replace('-','/'),r.cells[2],r.cells[3],r.cells[5],r.cells[6])},onRow={index -> rowIds.getOrNull(index)?.let(onRecord)},total=preview?.total ?: "0.00")
            if(!historyOnly) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                listOf("导出表格" to "xlsx","导出图片" to "png","导出 PDF" to "pdf").forEachIndexed {index,(label,kind) ->
                    val tint=listOf(Color(0xFF176D69),Color(0xFF276CB5),Color(0xFFAA4D44))[index]
                    val background=listOf(Color(0xFFDDF1E7),Color(0xFFE3EFFB),Color(0xFFF9E7E2))[index]
                    Card(onClick={if(!busy && project.isNotBlank() && start<=end)beginExport(kind)},modifier=Modifier.weight(1f).heightIn(min=96.dp),shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=background)) {
                        Column(Modifier.fillMaxWidth().padding(vertical=12.dp,horizontal=4.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Image(painterResource(when(kind) {"xlsx"->R.drawable.export_sheet;"png"->R.drawable.export_image;else->R.drawable.export_pdf}),null,Modifier.size(32.dp))
                            Text(label,fontSize=16.sp,color=tint)
                        }
                    }
                }
            }
            if(!historyOnly) TextButton({editingColumns=!editingColumns},contentPadding=PaddingValues(horizontal=4.dp)) {Text("导出设置 ${if(editingColumns) "⌃" else "⌄"}",fontSize=14.sp)}
            if(editingColumns) {
                columns.forEachIndexed {i,value -> TextField(defaultColumnNames[i],value,{next -> columns=columns.toMutableList().also {it[i]=next}})}
                MainButton("保存列名",!busy) {task {
                    require(columns.all {it.isNotBlank() && it.length<=24}) {"列名需完整，每列最多24字"}
                    repo.dao.preference(com.easyledger.app.data.local.PreferenceEntity("settlement_columns",org.json.JSONArray(columns).toString()))
                    "列名已保存。"
                }}
            }
        } else {
            MainButton("保存本地备份",!busy) {beginExport("backup")}
            MainButton("选择备份并恢复",!busy) {open.launch(arrayOf("application/json","text/plain","application/octet-stream"))}
            Text("恢复保留已有记录；冲突时整次拒绝，不覆盖、不清库。备份未加密，请保存到私人位置并另留一份。")
            OutlinedButton(onBack,Modifier.fillMaxWidth().heightIn(min=56.dp),enabled=!busy) {Text("返回")}
        }
        if(busy) Text("正在生成，请稍候…")
        if(message.isNotBlank()) Text(message)
    }
    if(choosingMonth) MonthPicker(selectedMonth,{choosingMonth=false}) {next -> month=next.toString();customRange=false;choosingMonth=false}
    if(naming) AlertDialog(onDismissRequest={naming=false},title={Text("导出文件名")},text={Column {TextField("名称（无需填写扩展名）",fileBase,{fileBase=it});Text("非法字符会替换为下划线；保存时请确认系统显示的位置与文件名，避免覆盖。")}},confirmButton={TextButton({naming=false;generate(exportKind)},enabled=fileBase.isNotBlank()) {Text("生成文件")}},dismissButton={TextButton({naming=false}) {Text("取消")}})
    if(ready) AlertDialog(onDismissRequest={if(!saving) ready=false},title={Text("文件生成成功")},text={Column {Text(files.joinToString("\n") {File(it).name});if(files.size>1) Text("图片共 ${files.size} 页。保存时依次选择每一页位置；分享会发送全部 PNG 图片。")}},confirmButton={TextButton({savePage=0;saving=true},enabled=!busy && !saving) {Text("保存文件")}},dismissButton={Row {TextButton({share()},enabled=!saving) {Text("分享文件")};TextButton({ready=false},enabled=!saving) {Text("关闭")}}})
    if(restoreConfirm) AlertDialog(onDismissRequest={if(!busy)restoreConfirm=false},title={Text("确认恢复备份？")},text={Text("${incoming?.projects?.size ?: 0} 个项目\n${incoming?.activeRecords?.size ?: 0} 条有效记录\n合计 ${incoming?.total ?: ""} 元\n现有历史不覆盖；冲突时拒绝整次恢复。")},confirmButton={TextButton({val snapshot=incoming ?: return@TextButton;restoreConfirm=false;task {repo.restore(snapshot);val latest=repo.backup();withContext(Dispatchers.Main) {data=latest};"恢复成功，金额已核对。"}},enabled=!busy) {Text("确认恢复")}},dismissButton={TextButton({restoreConfirm=false}) {Text("取消")}})
}
