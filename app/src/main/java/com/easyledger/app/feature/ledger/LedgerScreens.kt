package com.easyledger.app.feature.ledger

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easyledger.app.data.local.*
import com.easyledger.app.domain.calculation.Money
import com.easyledger.app.domain.model.BillingMode
import com.easyledger.app.ui.components.InformationCard
import com.easyledger.app.ui.components.PageContent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.easyledger.app.domain.time.*

@Composable
fun MainButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled) { Text(text) }
}

@Composable
fun TextField(label: String, value: String, onChange: (String) -> Unit, decimal: Boolean = false, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onChange, modifier = modifier.fillMaxWidth(), label = { Text(label,fontSize=14.sp) },
        singleLine = true,shape=RoundedCornerShape(12.dp),textStyle=MaterialTheme.typography.bodyLarge.copy(fontSize=17.sp,lineHeight=24.sp),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, label: String) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        trailingIcon = { if (value.isNotEmpty()) TextButton({ onChange("") }) { Text("清空") } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
}

@Composable
fun ConfirmDelete(title: String, message: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onConfirm, enabled = !busy) { Text("确认删除") } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("取消") } })
}

@Composable
fun ModePicker(value: BillingMode, onChange: (BillingMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("计费方式：${value.label} ▾") }
        DropdownMenu(open, { open = false }) {
            BillingMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label) }, onClick = { onChange(mode); open = false }) }
        }
    }
}

@Composable
fun rememberExitGuard(onBack: () -> Unit, busy: Boolean): () -> Unit {
    var confirm by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (!busy) confirm = true }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("放弃本次填写？") },
        text = { Text("尚未保存的内容会丢失。") },
        confirmButton = { TextButton({ confirm = false; onBack() }) { Text("放弃填写") } },
        dismissButton = { TextButton({ confirm = false }) { Text("继续填写") } })
    return { confirm = true }
}

@Composable
fun ProjectList(model: LedgerViewModel, onCreate: () -> Unit, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    val projects by model.projects.collectAsStateWithLifecycle()
    val records by model.allRecords.collectAsStateWithLifecycle()
    val keyword by model.projectSearch.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<ProjectEntity?>(null) }
    LazyColumn(modifier.testTag("project_list").padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("项目管理", style = MaterialTheme.typography.headlineMedium) }
        item { SearchField(keyword, { model.projectSearch.value = it }, "搜索项目名称或备注") }
        item { TextButton(onCreate,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!busy) { Text("＋ 创建项目") } }
            if (projects.isEmpty()) item { InformationCard(if (keyword.isBlank()) "暂无项目" else "没有找到项目", "可以创建项目，或清空关键词再试一次。") }
            items(projects, key = { it.id }) { project ->
                Card(onClick={onEdit(project.id)},modifier=Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        Text("${BillingMode.from(project.defaultMode).label} · ${project.defaultPrice} 元/${project.defaultUnit}")
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                            Text("›",Modifier.padding(12.dp))
                            var menu by remember { mutableStateOf(false) }
                            Box { TextButton({menu=true}) { Text("⋯") };DropdownMenu(menu,{menu=false}) { DropdownMenuItem({Text("删除项目")},{menu=false;deleting=project}) } }
                        }
                    }
                }
            }
    }
    deleting?.let { project -> ConfirmDelete("删除项目？", "删除“${project.name}”后，已有工作记录仍保留在历史记录中。", busy,
        { deleting = null }, { model.deleteProject(project.id) { deleting = null } }) }
}

@Composable
fun ProjectEditor(project: ProjectEntity?, model: LedgerViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val draftId = rememberSaveable { java.util.UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(project?.name ?: "") }
    var note by rememberSaveable { mutableStateOf(project?.note ?: "") }
    var mode by rememberSaveable { mutableStateOf(project?.defaultMode ?: BillingMode.HOUR.name) }
    var unit by rememberSaveable { mutableStateOf(project?.defaultUnit ?: "小时") }
    var price by rememberSaveable { mutableStateOf(project?.defaultPrice ?: "") }
    val busy by model.busy.collectAsStateWithLifecycle()
    val exit = rememberExitGuard(onBack, busy)
    var showNote by rememberSaveable {mutableStateOf(project?.note?.isNotBlank()==true)}
    PageContent(if (project == null) "创建项目" else "修改项目", modifier) {
        Text("基本信息",style=MaterialTheme.typography.titleMedium)
        TextField("项目名称", name, { name = it })
        Text("计费设置",style=MaterialTheme.typography.titleMedium)
        ModePicker(BillingMode.from(mode)) { mode = it.name; unit = it.unit }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            TextField("单位",unit,{unit=it},modifier=Modifier.weight(.8f))
            TextField("默认单价（元）",price,{price=it},true,Modifier.weight(1.2f))
        }
        TextButton({showNote=!showNote},contentPadding=PaddingValues(0.dp)) {Text("项目备注（选填） ${if(showNote) "⌃" else "⌄"}",fontSize=15.sp)}
        if(showNote) TextField("备注",note,{note=it})
        Text("默认单价仅用于新记录。",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(exit,Modifier.weight(1f).heightIn(min=56.dp),enabled=!busy,shape=RoundedCornerShape(12.dp)) {Text("取消")}
            Button({model.saveProject(project?.id,name,note,BillingMode.from(mode),unit,price,onBack,draftId)},Modifier.weight(1f).heightIn(min=56.dp),enabled=!busy,shape=RoundedCornerShape(12.dp)) {Text(if(busy) "保存中…" else "保存")}
        }
    }
}

@Composable
fun HistoryScreen(model: LedgerViewModel, onRecord: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    com.easyledger.app.feature.tools.ToolsScreen(onBack,modifier,historyOnly=true,onRecord=onRecord)

}

@Composable
fun RecordEditor(record: WorkRecordEntity?, model: LedgerViewModel, onCreateProject: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier, initialSpans: List<TimeSpan> = emptyList()) {
    val projects by model.allProjects.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val available = projects.filter { it.deletedAt == null || it.id == record?.projectId }
    val draftId = rememberSaveable { java.util.UUID.randomUUID().toString() }
    var projectId by rememberSaveable { mutableStateOf(record?.projectId ?: "") }
    var date by rememberSaveable { mutableStateOf(record?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Calendar.getInstance().time)) }
    var mode by rememberSaveable { mutableStateOf(record?.mode ?: BillingMode.HOUR.name) }
    var unit by rememberSaveable { mutableStateOf(record?.unit ?: "小时") }
    var quantity by rememberSaveable { mutableStateOf(record?.quantity ?: "") }
    var price by rememberSaveable { mutableStateOf(record?.unitPrice ?: "") }
    var note by rememberSaveable { mutableStateOf(record?.note ?: "") }
    var savedSpans by rememberSaveable { mutableStateOf(if(record==null && initialSpans.isEmpty()) listOf("${date}T07:00|${date}T11:00","${date}T13:00|${date}T17:00") else initialSpans.map { it.start + "|" + it.end }) }
    val spans = savedSpans.map { TimeSpan(it.substringBefore("|"), it.substringAfter("|")) }
    var useTime by rememberSaveable { mutableStateOf(record == null || record.quantitySource == "TIME") }
    var defaultsExpanded by rememberSaveable { mutableStateOf(false) }
    var timeExpanded by rememberSaveable { mutableStateOf(true) }
    val timeResult = runCatching { WorkTime.minutes(date, spans) }
    val billed = if(useTime && spans.isNotEmpty() && timeResult.isSuccess) WorkTime.hours(timeResult.getOrThrow()) else quantity
    fun changeDate(next: String) { savedSpans = WorkTime.shift(spans,date,next).map { it.start + "|" + it.end }; date=next }
    var chooseProject by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    val exit = rememberExitGuard(onBack, busy)
    val context = LocalContext.current
    val allRecords by model.allRecords.collectAsStateWithLifecycle()
    val allSegments by model.segments.collectAsStateWithLifecycle()
    val duplicate = allRecords.any { it.record.id != record?.id && it.record.projectId == projectId && it.record.date == date &&
        it.record.quantity.toBigDecimalOrNull()?.compareTo(quantity.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) == 0 &&
        it.record.unitPrice.toBigDecimalOrNull()?.compareTo(price.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) == 0 }
    val calculation = runCatching { Money.amount(billed, price, BillingMode.from(mode), record?.unitPrice).toPlainString() }
    val amount = calculation.getOrNull()
    fun choose(p: ProjectEntity) {
        projectId = p.id
        if (record == null) { mode = p.defaultMode; unit = p.defaultUnit; price = p.defaultPrice;useTime=mode==BillingMode.HOUR.name }
        chooseProject = false
    }
    LaunchedEffect(available.map { it.id }) { if(projectId.isBlank() && available.isNotEmpty()) choose(available.first()) }
    PageContent(if (record == null) "新增工作记录" else "修改工作记录", modifier) {
        if(available.isEmpty()) {
            Text("请先创建一个工作项目。")
            MainButton("创建项目",!busy,onCreateProject)
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton({chooseProject=true},Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=!busy,shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=8.dp)) {Text("${available.firstOrNull {it.id==projectId}?.name ?: "选择项目"} ▾",fontSize=16.sp,maxLines=2)}
                DropdownMenu(chooseProject,{chooseProject=false}) {available.forEach {p -> DropdownMenuItem({Text(p.name)},{choose(p)})}}
            }
            OutlinedButton({
                val d=java.time.LocalDate.parse(date)
                DatePickerDialog(context,{_,y,m,day -> changeDate(java.time.LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()
            },Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=6.dp)) {Text("$date ▾",fontSize=15.sp)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
            listOf("今天" to 0,"昨天" to -1,"前天" to -2).forEach {(label,offset) ->
                TextButton({changeDate(java.time.LocalDate.now().plusDays(offset.toLong()).toString())},Modifier.heightIn(min=40.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp)) {Text(label,fontSize=14.sp)}
            }
        }
        TextButton({timeExpanded=!timeExpanded},Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("◷ 工作时间 ${if(timeExpanded) "⌃" else "⌄"}") }
        if(timeExpanded) {
            TimeInputs(date, spans) { next -> savedSpans=next.map { it.start+"|"+it.end }; if(next.isEmpty()) useTime=false }
            if(spans.isNotEmpty() && mode==BillingMode.HOUR.name) Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Checkbox(useTime,{useTime=it});Text("自动计费工时（关闭可手动调整）",fontSize=14.sp) }
            if(timeResult.isFailure) Text(timeResult.exceptionOrNull()?.message.orEmpty(),color=MaterialTheme.colorScheme.error)
        }
        if(!useTime || mode!=BillingMode.HOUR.name) TextField("计费数量（$unit）",quantity,{quantity=it},true)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color(0xFFEAF5F1))) { Column(Modifier.padding(16.dp)) { Text("总工时");Text("${timeResult.getOrNull()?.let { WorkTime.hours(it) } ?: "—"} 小时",style=MaterialTheme.typography.titleLarge) } }
            Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color(0xFFEAF5F1))) { Column(Modifier.padding(16.dp)) { Text("金额（元）");Text(amount ?: "—",style=MaterialTheme.typography.titleLarge) } }
        }
        if(amount==null) Text(calculation.exceptionOrNull()?.message ?: "请填写数量和单价",color=MaterialTheme.colorScheme.error)
        TextButton({defaultsExpanded=!defaultsExpanded},Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("⚙ 默认配置（单价 / 备注） ${if(defaultsExpanded) "⌃" else "⌄"}") }
        if(defaultsExpanded) {
            ModePicker(BillingMode.from(mode)) { mode=it.name;unit=it.unit;if(it!=BillingMode.HOUR)useTime=false }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TextField("单位",unit,{unit=it},modifier=Modifier.weight(.8f))
                TextField("单价（元）",price,{price=it},true,Modifier.weight(1.2f))
            }
            TextField("备注（可不填）",note,{note=it})
            if(record==null) TextButton({
                val previousDate=java.time.LocalDate.parse(date).minusDays(1).toString()
                val previous=allRecords.firstOrNull { it.record.projectId==projectId && it.record.date==previousDate }?.record
                if(previous!=null) { mode=previous.mode;unit=previous.unit;quantity=previous.quantity;price=previous.unitPrice;note=previous.note;useTime=previous.quantitySource=="TIME";savedSpans=WorkTime.shift(allSegments.orEmpty().filter { it.recordId==previous.id }.map { TimeSpan(it.startDateTime,it.endDateTime) },previous.date,date).map { it.start+"|"+it.end } }
            }) { Text("复制上一天") }
        }
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(exit,Modifier.weight(1f).heightIn(min=56.dp),enabled=!busy,shape=RoundedCornerShape(12.dp)) {Text("取消")}
            Button({confirmSave=true},Modifier.weight(1f).heightIn(min=56.dp),enabled=!busy && amount!=null && timeResult.isSuccess && projectId.isNotBlank() && unit.isNotBlank(),shape=RoundedCornerShape(12.dp)) {Text(if(busy) "保存中…" else "保存")}
        }
    }
    if (confirmSave) AlertDialog(onDismissRequest = { if (!busy) confirmSave = false }, title = { Text("确认保存这条记录？") },
        text = { Text("$date\n${available.firstOrNull { it.id == projectId }?.name ?: ""}\n数量 $billed $unit\n单价 $price 元\n金额 $amount 元\n${if (duplicate) "提醒：已有同项目、日期、数量和单价的记录，可能重复，请核对。" else "同一天允许多条记录。"}") },
        confirmButton = { TextButton({ model.saveRecord(record?.id, projectId, date, BillingMode.from(mode), unit, billed, price, note, { confirmSave = false; onBack() }, draftId, spans, useTime) }, enabled = !busy) { Text("确认保存") } },
        dismissButton = { TextButton({ confirmSave = false }, enabled = !busy) { Text("返回核对") } })
}

@Composable
fun RecordDetail(item: RecordWithProject, model: LedgerViewModel, onEdit: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier, spans: List<TimeSpan> = emptyList()) {
    val busy by model.busy.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf(false) }
    PageContent("记录详情", modifier) {
        InformationCard(item.projectName, "日期：${item.record.date}\n计费：${BillingMode.from(item.record.mode).label}\n数量：${item.record.quantity} ${item.record.unit}\n单价：${item.record.unitPrice} 元\n金额：${item.record.amount} 元\n备注：${item.record.note.ifBlank { "无" }}")
        if(spans.isNotEmpty()) InformationCard("原始时间段", spans.joinToString("\n") { "${it.start.replace('T',' ')}–${it.end.replace('T',' ')}" } + "\n实际 ${item.record.actualMinutes} 分钟；计费数量独立保存。")
        MainButton("修改记录", !busy, onEdit)
        OutlinedButton({ deleting = true }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) { Text("删除记录") }
        TextButton(onBack, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("返回历史记录") }
    }
    if (deleting) ConfirmDelete("删除这条记录？", "此记录将从历史列表中移除，请仔细确认。", busy, { deleting = false }, { model.deleteRecord(item.record.id, onBack) })
}

@Composable
fun OnboardingScreen(model: LedgerViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val busy by model.busy.collectAsStateWithLifecycle()
    val tips = listOf("先创建项目" to "在“项目”页面点“创建项目”，填写名称和常用单价。", "再新增记录" to "首页点“新增工作记录”，选日期，填数量和单价，核对金额后保存。", "查看和查找记录" to "首页点“历史记录”，可以搜索项目名和备注，点击记录查看或修改。")
    BackHandler { if (step > 0) step-- else model.finishOnboarding(onDone) }
    PageContent("欢迎使用简单记账助手", modifier) {
        Text("第 ${step + 1} 步，共 ${tips.size} 步")
        InformationCard(tips[step].first, tips[step].second)
        MainButton(if (step == tips.lastIndex) "完成" else "下一步", !busy) { if (step == tips.lastIndex) model.finishOnboarding(onDone) else step++ }
        if (step > 0) OutlinedButton({ step-- }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) { Text("上一步") }
        TextButton({ model.finishOnboarding(onDone) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) { Text("跳过") }
    }
}
