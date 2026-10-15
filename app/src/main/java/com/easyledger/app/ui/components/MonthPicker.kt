package com.easyledger.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.YearMonth

@Composable
fun MonthPicker(value: YearMonth, onDismiss: () -> Unit, onChoose: (YearMonth) -> Unit) {
    var year by remember {mutableIntStateOf(value.year)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("选择年月")},text={Column {
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            TextButton({if(year>1900)year--}) {Text("‹")}
            Text("${year}年",Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            TextButton({if(year<9999)year++}) {Text("›")}
        }
        (0..3).forEach {row -> Row(Modifier.fillMaxWidth()) {
            (1..3).forEach {column -> val month=row*3+column
                TextButton({onChoose(YearMonth.of(year,month))},Modifier.weight(1f).heightIn(min=48.dp)) {Text("${month}月",color=if(year==value.year && month==value.monthValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}
            }
        } }
    }},confirmButton={TextButton(onDismiss) {Text("取消")}})
}
