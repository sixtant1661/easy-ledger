package com.easyledger.app.feature.ledger

import androidx.compose.foundation.layout.*
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
fun BatchScreen(model: LedgerViewModel, onBack: () -> Unit, modifier: Modifier = Modifier, onHistory: () -> Unit = onBack) {
    val projects by model.allProjects.collectAsStateWithLifecycle()
    val records by model.allRecords.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val batchId=rememberSaveable {java.util.UUID.randomUUID().toString()}
    var projectId by rememberSaveable {mutableStateOf("")}
    var start by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var end by rememberSaveable {mutableStateOf(start)}
    var mode by rememberSaveable {mutableStateOf(BillingMode.HOUR.name)}
    var unit by rememberSaveable {mutableStateOf("小时")}
    var qty by rememberSaveable {mutableStateOf("")}
    var price by rememberSaveable {mutableStateOf("")}
    var note by rememberSaveable {mutableStateOf("")}
    var savedSpans by rememberSaveable {mutableStateOf(listOf("${start}T07:00|${start}T11:00","${start}T13:00|${start}T17:00"))}
    val spans=savedSpans.map {TimeSpan(it.substringBefore("|"),it.substringAfter("|"))}
    var excluded by rememberSaveable {mutableStateOf(emptyList<String>())}
    var skipWeekend by rememberSaveable {mutableStateOf(false)}
    var skipExisting by rememberSaveable {mutableStateOf(true)}
    var useTime by rememberSaveable {mutableStateOf(true)}
    var open by remember {mutableStateOf(false)}
    var defaults by rememberSaveable {mutableStateOf(false)}
    var timeExpanded by rememberSaveable {mutableStateOf(true)}
    var stage by rememberSaveable {mutableIntStateOf(0)}
    var savedCount by rememberSaveable {mutableIntStateOf(0)}
    var savedTotal by rememberSaveable {mutableStateOf("")}
    val exit=rememberExitGuard(onBack,busy)
    val range=runCatching {WorkTime.dates(start,end)}
    val existing=records.filter {it.record.projectId==projectId}.map {it.record.date}.toSet()
    val allDates=range.getOrDefault(emptyList())
    val selected=allDates.filter {it !in excluded && (!skipWeekend || LocalDate.parse(it).dayOfWeek.value<6) && (!skipExisting || it !in existing)}
    val minutes=runCatching {WorkTime.minutes(start,spans)}
    val billed=if(useTime && spans.isNotEmpty() && minutes.isSuccess) WorkTime.hours(minutes.getOrThrow()) else qty
    val amount=runCatching {Money.amount(billed,price,BillingMode.from(mode)).toPlainString()}
    val total=amount.getOrNull()?.let {Money.total(List(selected.size) {_ -> it})} ?: "—"
    LaunchedEffect(projects.map {it.id}) {if(projectId.isBlank()) projects.firstOrNull {it.deletedAt==null}?.let {projectId=it.id;mode=it.defaultMode;unit=it.defaultUnit;price=it.defaultPrice;useTime=mode==BillingMode.HOUR.name}}
    val valid=!busy && projectId.isNotBlank() && unit.isNotBlank() && range.isSuccess && amount.isSuccess && minutes.isSuccess && selected.isNotEmpty()
    PageContent(when(stage) {0->"批量增加";1->"批量增加 · 预览";else->"添加完成"},modifier,onBack={if(stage==1)stage=0 else if(stage==2)onBack() else exit()},backEnabled=!busy) {
        when(stage) {
            0 -> {
                Box {
                    SolidSelector(projects.firstOrNull {it.id==projectId}?.name ?: "选择项目",{open=true},Modifier.fillMaxWidth(),enabled=!busy)
                    DropdownMenu(open,{open=false}) {projects.filter {it.deletedAt==null}.forEach {p -> DropdownMenuItem({Text(p.name)},{projectId=p.id;mode=p.defaultMode;unit=p.defaultUnit;price=p.defaultPrice;useTime=mode==BillingMode.HOUR.name;open=false})}}
                }
                Text("▦ 日期范围",style=MaterialTheme.typography.titleMedium)
                val crossYear=LocalDate.parse(start).year!=LocalDate.parse(end).year
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    DateButton("开始日期",start,Modifier.weight(1f),showYear=crossYear) {next -> savedSpans=WorkTime.shift(spans,start,next).map {it.start+"|"+it.end};start=next;excluded=emptyList()}
                    DateButton("结束日期",end,Modifier.weight(1f),showYear=crossYear) {end=it;excluded=emptyList()}
                }
                FoldHeader("工作时间",timeExpanded,{timeExpanded=!timeExpanded})
                if(timeExpanded) {
                    TimeInputs(start,spans) {next -> savedSpans=next.map {it.start+"|"+it.end};if(next.isEmpty())useTime=false}
                    if(mode==BillingMode.HOUR.name && spans.isNotEmpty()) Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Checkbox(useTime,{useTime=it});Text("自动计算每天计费小时")}
                }
                if(!useTime || mode!=BillingMode.HOUR.name) TextField("每天计费数量（$unit）",qty,{qty=it},true)
                FoldHeader("默认配置",defaults,{defaults=!defaults})
                Text("单价 $price 元/$unit",color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(defaults) {
                    ModePicker(BillingMode.from(mode)) {mode=it.name;unit=it.unit;if(it!=BillingMode.HOUR)useTime=false}
                    TextField("单位",unit,{unit=it})
                    TextField("单价（元）",price,{price=it},true)
                    TextField("备注（选填）",note,{note=it})
                }
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Checkbox(skipWeekend,{skipWeekend=it});Text("跳过周六、周日")}
                if(!valid) Text(range.exceptionOrNull()?.message ?: minutes.exceptionOrNull()?.message ?: amount.exceptionOrNull()?.message ?: "请选择项目和有效日期")
                MainButton("下一步 · 预览 ›",valid) {stage=1}
                OutlinedButton(exit,Modifier.fillMaxWidth().heightIn(min=56.dp),enabled=!busy) {Text("取消")}
            }
            1 -> {
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {Text("预计天数");Text("${selected.size} 天",style=MaterialTheme.typography.titleMedium)}
                        Column(Modifier.weight(1f)) {Text("每天数量");Text("$billed $unit",style=MaterialTheme.typography.titleMedium)}
                        Column(Modifier.weight(1.3f)) {Text("预计金额");Text("¥ $total",style=MaterialTheme.typography.titleMedium)}
                    }
                }
                Text("将生成 ${selected.size} 条记录；取消勾选休息日。")
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Checkbox(skipExisting,{skipExisting=it});Text("跳过已存在的日期")}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("选择 / 日期");Text("工作时间");Text("数量 / 金额")}
                allDates.forEach {day ->
                    val blocked=skipExisting && day in existing
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(day in selected,{checked ->
                            if(skipWeekend && LocalDate.parse(day).dayOfWeek.value>=6) {excluded=(excluded+allDates.filter {LocalDate.parse(it).dayOfWeek.value>=6}).distinct();skipWeekend=false}
                            excluded=if(checked) excluded-day else (excluded+day).distinct()
                        },enabled=!busy && !blocked)
                        Column(Modifier.weight(1f)) {
                            Text(day.substring(5).replace('-','/')+if(blocked) "（已有记录）" else "")
                            Text(spans.joinToString("\n") {it.start.substringAfter('T')+"–"+(if(it.end.substringBefore('T')!=it.start.substringBefore('T')) "次日 " else "")+it.end.substringAfter('T')},style=MaterialTheme.typography.bodySmall)
                        }
                        Text("$billed\n${amount.getOrNull() ?: "—"}")
                    }
                    HorizontalDivider()
                }
                MainButton(if(busy) "正在保存…" else "确认添加（${selected.size} 条）",valid) {
                    val count=selected.size;val finalTotal=total
                    model.saveBatch(projectId,selected,BillingMode.from(mode),unit,billed,price,note,spans,start,useTime,batchId) {savedCount=count;savedTotal=finalTotal;stage=2}
                }
            }
            else -> {
                Spacer(Modifier.height(40.dp))
                Text("✓",Modifier.fillMaxWidth(),style=MaterialTheme.typography.displayLarge,color=MaterialTheme.colorScheme.primary,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Text("已成功添加 $savedCount 条记录",Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Text("总金额：¥ $savedTotal",Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                MainButton("查看新增记录",onClick=onHistory)
            }
        }
    }
}
