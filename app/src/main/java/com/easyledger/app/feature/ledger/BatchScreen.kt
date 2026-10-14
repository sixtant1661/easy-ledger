package com.easyledger.app.feature.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easyledger.app.domain.calculation.Money
import com.easyledger.app.domain.model.BillingMode
import com.easyledger.app.domain.time.*
import com.easyledger.app.ui.components.*
import java.time.LocalDate

@Composable
fun BatchScreen(model: LedgerViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val projects by model.allProjects.collectAsStateWithLifecycle()
    val records by model.allRecords.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val batchId = rememberSaveable { java.util.UUID.randomUUID().toString() }
    var projectId by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var end by rememberSaveable { mutableStateOf(start) }
    var mode by rememberSaveable { mutableStateOf(BillingMode.HOUR.name) }
    var unit by rememberSaveable { mutableStateOf("小时") }
    var qty by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var savedSpans by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val spans = savedSpans.map { TimeSpan(it.substringBefore("|"),it.substringAfter("|")) }
    var excluded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var skipWeekend by rememberSaveable { mutableStateOf(false) }
    var useTime by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    val exit = rememberExitGuard(onBack,busy)
    val dates = runCatching { WorkTime.dates(start,end).filter { it !in excluded && (!skipWeekend || LocalDate.parse(it).dayOfWeek.value < 6) } }
    val minutes = runCatching { WorkTime.minutes(start,spans) }
    val billed = if(useTime && minutes.isSuccess) WorkTime.hours(minutes.getOrThrow()) else qty
    val amount = runCatching { Money.amount(billed,price,BillingMode.from(mode)).toPlainString() }
    PageContent("批量补录",modifier) {
        Text("同一模板应用到所选日期；时间段自动移动到各工作日。休息日可取消勾选。")
        Box {
            OutlinedButton({ open=true },Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("项目：${projects.firstOrNull { it.id==projectId }?.name ?: "请选择"} ▾") }
            DropdownMenu(open,{open=false}) { projects.filter { it.deletedAt==null }.forEach { p -> DropdownMenuItem({Text(p.name)},{projectId=p.id;mode=p.defaultMode;unit=p.defaultUnit;price=p.defaultPrice;open=false}) } }
        }
        DateButton("开始日期",start) { next -> savedSpans=WorkTime.shift(spans,start,next).map { it.start+"|"+it.end };start=next;excluded=emptyList() }
        DateButton("结束日期",end) { end=it;excluded=emptyList() }
        Row { Checkbox(skipWeekend,{skipWeekend=it});Text("跳过周六、周日（可再手动选择）",Modifier.padding(top=12.dp)) }
        ModePicker(BillingMode.from(mode)) { mode=it.name;unit=it.unit;if(it!=BillingMode.HOUR)useTime=false }
        TimeInputs(start,spans) { next -> savedSpans=next.map { it.start+"|"+it.end };if(next.isEmpty())useTime=false }
        if(spans.isNotEmpty()) {
            Text(minutes.getOrNull()?.let { "每天实际 $it 分钟" } ?: minutes.exceptionOrNull()?.message.orEmpty())
            if(mode==BillingMode.HOUR.name) Row { Checkbox(useTime,{useTime=it});Text("按时间自动计算计费小时",Modifier.padding(top=12.dp)) }
        }
        TextField("单位",unit,{unit=it})
        if(useTime) Text("每天计费 $billed 小时") else TextField("每天计费数量",qty,{qty=it},true)
        TextField("单价（元）",price,{price=it},true)
        TextField("每天备注",note,{note=it})
        val allDates = runCatching { WorkTime.dates(start,end) }.getOrDefault(emptyList())
        allDates.forEach { day -> Row { Checkbox(day !in excluded && (!skipWeekend || LocalDate.parse(day).dayOfWeek.value<6),{checked ->
            if(skipWeekend && LocalDate.parse(day).dayOfWeek.value>=6) { excluded=(excluded+allDates.filter { LocalDate.parse(it).dayOfWeek.value>=6 }).distinct();skipWeekend=false }
            excluded=if(checked) excluded-day else excluded+day
        });Text(day,Modifier.padding(top=12.dp)) } }
        val selected=dates.getOrDefault(emptyList())
        InformationCard("待保存预览", if(amount.isSuccess && minutes.isSuccess && selected.isNotEmpty()) "${selected.size} 天，每天 ${amount.getOrThrow()} 元\n合计 ${Money.total(List(selected.size) { amount.getOrThrow() })} 元" else (dates.exceptionOrNull()?.message ?: minutes.exceptionOrNull()?.message ?: amount.exceptionOrNull()?.message ?: "请选择日期"))
        MainButton("核对整批记录",!busy && projectId.isNotBlank() && unit.isNotBlank() && amount.isSuccess && minutes.isSuccess && selected.isNotEmpty()) { preview=true }
        OutlinedButton(exit,Modifier.fillMaxWidth().heightIn(min=56.dp),enabled=!busy) { Text("取消") }
    }
    if(preview) {
        val selected=dates.getOrThrow()
        val duplicates=records.count { it.record.projectId==projectId && it.record.date in selected }
        AlertDialog(onDismissRequest={if(!busy)preview=false},title={Text("确认保存 ${selected.size} 天？")},text={
            Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())) {
                Text("${selected.first()} 至 ${selected.last()}\n数量 $billed $unit/天，单价 $price 元\n合计 ${Money.total(List(selected.size) { amount.getOrThrow() })} 元\n已有 $duplicates 条同日记录，请核对日期勾选；同日多条允许。\n全部保存或全部取消，不会只保存一半。\n保存日期：\n${selected.joinToString("\n")}")
            }
        },confirmButton={TextButton({model.saveBatch(projectId,selected,BillingMode.from(mode),unit,billed,price,note,spans,start,useTime,batchId){preview=false;onBack()}},enabled=!busy){Text("确认保存整批")}},dismissButton={TextButton({preview=false},enabled=!busy){Text("返回核对")}})
    }
}
