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
    spans.forEachIndexed { index, span ->
        val start = LocalDateTime.parse(span.start); val end = LocalDateTime.parse(span.end)
        fun replace(a: LocalDateTime = start, b: LocalDateTime = end) { onChange(spans.toMutableList().also { it[index] = TimeSpan(a.toString(), b.toString()) }) }
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            TextButton({ TimePickerDialog(context,{_,h,m -> replace(a=LocalDate.parse(date).atTime(h,m))},start.hour,start.minute,true).show() },Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(2.dp)) { Text("起 ${start.toLocalTime()}") }
            TextButton({ TimePickerDialog(context,{_,h,m -> replace(b=end.toLocalDate().atTime(h,m))},end.hour,end.minute,true).show() },Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(2.dp)) { Text("止 ${end.toLocalTime()}") }
            Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                Checkbox(end.toLocalDate()!=start.toLocalDate(),{ next -> replace(b=LocalDate.parse(date).plusDays(if(next) 1 else 0).atTime(end.toLocalTime())) },Modifier.size(48.dp))
                Text("次日",style=MaterialTheme.typography.bodySmall)
            }
            TextButton({onChange(spans.filterIndexed { i,_ -> i!=index })},Modifier.width(48.dp),contentPadding=PaddingValues(0.dp)) { Text("×") }
        }
    }
    TextButton({ val day=LocalDate.parse(date);onChange(spans+TimeSpan(day.atTime(7,0).toString(),day.atTime(11,0).toString())) },Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("⊕ 添加时间段") }
}
