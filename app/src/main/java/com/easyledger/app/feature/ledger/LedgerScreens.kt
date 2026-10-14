package com.easyledger.app.feature.ledger

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easyledger.app.data.local.*
import com.easyledger.app.domain.calculation.Money
import com.easyledger.app.domain.model.BillingMode
import com.easyledger.app.ui.components.InformationCard
import com.easyledger.app.ui.components.PageContent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun MainButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled) { Text(text) }
}

@Composable
fun TextField(label: String, value: String, onChange: (String) -> Unit, decimal: Boolean = false) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        singleLine = decimal, keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        trailingIcon = { if (value.isNotEmpty()) TextButton({ onChange("") }) { Text("清空") } })
}

@Composable
fun ConfirmDelete(title: String, message: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onConfirm, enabled = !busy) { Text("确认删除") } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("取消") } })
}

@Composable
private fun ModePicker(value: BillingMode, onChange: (BillingMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("计费方式：${value.label} ▾") }
        DropdownMenu(open, { open = false }) {
            BillingMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label) }, onClick = { onChange(mode); open = false }) }
        }
    }
}

@Composable
private fun rememberExitGuard(onBack: () -> Unit, busy: Boolean): () -> Unit {
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
        item { Text("项目列表", style = MaterialTheme.typography.headlineMedium) }
        item { SearchField(keyword, { model.projectSearch.value = it }, "搜索项目名称或备注") }
        item { MainButton("创建项目", !busy, onCreate) }
            if (projects.isEmpty()) item { InformationCard(if (keyword.isBlank()) "暂无项目" else "没有找到项目", "可以创建项目，或清空关键词再试一次。") }
            items(projects, key = { it.id }) { project ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        Text("累计 ${Money.total(records.filter { it.record.projectId == project.id }.map { it.record.amount })} 元")
                        Text("${BillingMode.from(project.defaultMode).label} · ${project.defaultPrice} 元/${project.defaultUnit}")
                        if (project.note.isNotEmpty()) Text(project.note)
                        OutlinedButton({ onEdit(project.id) }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("修改项目") }
                        TextButton({ deleting = project }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) { Text("删除项目") }
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
    PageContent(if (project == null) "创建项目" else "修改项目", modifier) {
        TextField("项目名称", name, { name = it })
        ModePicker(BillingMode.from(mode)) { mode = it.name; unit = it.unit }
        TextField("单位", unit, { unit = it })
        TextField("默认单价（元）", price, { price = it }, decimal = true)
        TextField("项目备注（可不填）", note, { note = it })
        Text("默认单价只用于新记录，不会改变历史记录的单价。")
        MainButton(if (busy) "正在保存…" else "保存项目", !busy) { model.saveProject(project?.id, name, note, BillingMode.from(mode), unit, price, onBack, draftId) }
        OutlinedButton(exit, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) { Text("取消") }
    }
}

@Composable
fun HistoryScreen(model: LedgerViewModel, onRecord: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val records by model.records.collectAsStateWithLifecycle()
    val keyword by model.recordSearch.collectAsStateWithLifecycle()
    LazyColumn(modifier.testTag("record_list").padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("历史记录", style = MaterialTheme.typography.headlineMedium) }
        item { SearchField(keyword, { model.recordSearch.value = it }, "搜索项目名称或记录备注") }
        item { Text("${records.size} 条记录 · ${if (keyword.isBlank()) "合计" else "搜索结果合计"} ${Money.total(records.map { it.record.amount })} 元") }
            if (records.isEmpty()) item { InformationCard("没有找到记录", "还没有记录，或没有匹配的关键词。可以先新增工作记录。") }
            items(records, key = { it.record.id }) { item ->
                Card(onClick = { onRecord(item.record.id) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.projectName, style = MaterialTheme.typography.titleMedium)
                        Text("${item.record.date} · ${item.record.amount} 元")
                        Text("${item.record.quantity} ${item.record.unit} × ${item.record.unitPrice} 元")
                        if (item.record.note.isNotEmpty()) Text(item.record.note)
                        if (item.projectDeletedAt != null) Text("项目已删除，记录仍保留")
                    }
                }
            }
        item { OutlinedButton(onBack, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("返回首页") } }
    }
}

@Composable
fun RecordEditor(record: WorkRecordEntity?, model: LedgerViewModel, onCreateProject: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
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
    var chooseProject by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    val exit = rememberExitGuard(onBack, busy)
    val context = LocalContext.current
    val allRecords by model.allRecords.collectAsStateWithLifecycle()
    val duplicate = allRecords.any { it.record.id != record?.id && it.record.projectId == projectId && it.record.date == date &&
        it.record.quantity.toBigDecimalOrNull()?.compareTo(quantity.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) == 0 &&
        it.record.unitPrice.toBigDecimalOrNull()?.compareTo(price.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) == 0 }
    val calculation = runCatching { Money.amount(quantity, price, BillingMode.from(mode)).toPlainString() }
    val amount = calculation.getOrNull()
    fun choose(p: ProjectEntity) {
        projectId = p.id
        if (record == null) { mode = p.defaultMode; unit = p.defaultUnit; price = p.defaultPrice }
        chooseProject = false
    }
    PageContent(if (record == null) "新增工作记录" else "修改工作记录", modifier) {
        if (available.isEmpty()) {
            InformationCard("请先创建项目", "工作记录需要归属于一个项目。创建项目后再回来填写。")
            MainButton("先创建项目", !busy, onCreateProject)
        } else {
            Box {
                OutlinedButton({ chooseProject = true }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) {
                    Text("项目：${available.firstOrNull { it.id == projectId }?.name ?: "请选择"} ▾")
                }
                DropdownMenu(chooseProject, { chooseProject = false }) {
                    available.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { choose(p) }) }
                }
            }
        }
        OutlinedButton({
            val calendar = Calendar.getInstance().apply { time = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date)!! }
            DatePickerDialog(context, { _, y, m, d -> date = String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d) },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("工作日期：$date（点此选择）") }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("今天" to 0, "昨天" to -1, "前天" to -2).forEach { (label, offset) ->
                TextButton({ val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }; date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(c.time) }, Modifier.weight(1f).heightIn(min = 56.dp)) { Text(label) }
            }
        }
        ModePicker(BillingMode.from(mode)) { mode = it.name; unit = it.unit }
        TextField("单位", unit, { unit = it })
        TextField("计费数量", quantity, { quantity = it }, decimal = true)
        TextField("单价（元）", price, { price = it }, decimal = true)
        TextField("备注（可不填）", note, { note = it })
        InformationCard("请核对金额", if (amount != null) "$amount 元" else "填写数量和单价后自动计算。${calculation.exceptionOrNull()?.message ?: ""}")
        Text("数量手动填写；按天计费不会自动换算工时。")
        MainButton(if (busy) "正在保存…" else "核对并保存", !busy && amount != null && projectId.isNotBlank() && unit.isNotBlank()) { confirmSave = true }
        OutlinedButton(exit, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) { Text("取消") }
    }
    if (confirmSave) AlertDialog(onDismissRequest = { if (!busy) confirmSave = false }, title = { Text("确认保存这条记录？") },
        text = { Text("$date\n${available.firstOrNull { it.id == projectId }?.name ?: ""}\n数量 $quantity $unit\n单价 $price 元\n金额 $amount 元\n${if (duplicate) "提醒：已有同项目、日期、数量和单价的记录，可能重复，请核对。" else "同一天允许多条记录。"}") },
        confirmButton = { TextButton({ model.saveRecord(record?.id, projectId, date, BillingMode.from(mode), unit, quantity, price, note, { confirmSave = false; onBack() }, draftId) }, enabled = !busy) { Text("确认保存") } },
        dismissButton = { TextButton({ confirmSave = false }, enabled = !busy) { Text("返回核对") } })
}

@Composable
fun RecordDetail(item: RecordWithProject, model: LedgerViewModel, onEdit: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val busy by model.busy.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf(false) }
    PageContent("记录详情", modifier) {
        InformationCard(item.projectName, "日期：${item.record.date}\n计费：${BillingMode.from(item.record.mode).label}\n数量：${item.record.quantity} ${item.record.unit}\n单价：${item.record.unitPrice} 元\n金额：${item.record.amount} 元\n备注：${item.record.note.ifBlank { "无" }}")
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
