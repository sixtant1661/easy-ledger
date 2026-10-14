package com.easyledger.app.feature.ledger

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.easyledger.app.domain.time.TimeSpan
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun DateButton(label: String, date: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton({
        val d = LocalDate.parse(date)
        DatePickerDialog(context, { _, y, m, day -> onChange(LocalDate.of(y,m+1,day).toString()) }, d.year, d.monthValue-1, d.dayOfMonth).show()
    }, Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("$label：$date（选择日期）") }
}

@Composable
fun TimeInputs(date: String, spans: List<TimeSpan>, onChange: (List<TimeSpan>) -> Unit) {
    val context = LocalContext.current
    Text("工作时间段（可不填）", style=MaterialTheme.typography.titleMedium)
    spans.forEachIndexed { index, span ->
        val start = LocalDateTime.parse(span.start); val end = LocalDateTime.parse(span.end)
        fun replace(a: LocalDateTime = start, b: LocalDateTime = end) { onChange(spans.toMutableList().also { it[index] = TimeSpan(a.toString(), b.toString()) }) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("第 ${index+1} 段")
                OutlinedButton({ TimePickerDialog(context, { _, h,m -> replace(a=LocalDate.parse(date).atTime(h,m)) }, start.hour,start.minute,true).show() }, Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("开始：${start.toLocalTime()}") }
                DateButton("结束日期", end.toLocalDate().toString()) { replace(b=LocalDate.parse(it).atTime(end.toLocalTime())) }
                OutlinedButton({ TimePickerDialog(context, { _, h,m -> replace(b=end.toLocalDate().atTime(h,m)) }, end.hour,end.minute,true).show() }, Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("结束：${end.toLocalTime()}") }
                TextButton({ onChange(spans.filterIndexed { i,_ -> i != index }) }, Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("删除第 ${index+1} 段") }
            }
        }
    }
    OutlinedButton({
        val day = LocalDate.parse(date)
        onChange(spans + TimeSpan(day.atTime(7,0).toString(),day.atTime(11,0).toString()))
    }, Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("添加工作时间段") }
    if(spans.isNotEmpty()) Text("跨午夜请明确选择次日结束日期；首尾相接允许，重叠不能保存。")
}
