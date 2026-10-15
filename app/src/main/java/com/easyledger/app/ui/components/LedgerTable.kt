package com.easyledger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** All columns share the available screen width. Values wrap rather than being clipped or ellipsized. */
@Composable
fun LedgerTable(headers: List<String>, rows: List<List<String>>, onRow: ((Int) -> Unit)? = null, total: String? = null) {
    val weights=if(headers.size==5) listOf(.15f,.30f,.12f,.18f,.25f) else listOf(.19f,.27f,.23f,.31f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(Color(0xFFEAF4F1)).padding(vertical=9.dp)) {
            headers.forEachIndexed {i,text -> Text(text,Modifier.weight(weights[i]).padding(horizontal=3.dp),fontSize=14.sp,lineHeight=20.sp,textAlign=if(i>=2) TextAlign.End else TextAlign.Start) }
        }
        if(rows.isEmpty()) Text("暂无匹配记录",Modifier.padding(vertical=20.dp,horizontal=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEachIndexed {index,cells ->
            Row(Modifier.fillMaxWidth().then(if(onRow!=null) Modifier.clickable {onRow(index)} else Modifier).heightIn(min=48.dp).padding(vertical=9.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                cells.forEachIndexed {i,text -> Text(text.ifBlank {"—"},Modifier.weight(weights[i]).padding(horizontal=3.dp),fontSize=15.sp,lineHeight=21.sp,textAlign=if(i>=2) TextAlign.End else TextAlign.Start) }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline)
        }
        if(total!=null) Row(Modifier.fillMaxWidth().background(Color(0xFFFFF5DC)).padding(10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("合计",Modifier.weight(.4f),style=MaterialTheme.typography.titleMedium)
            Text(total,Modifier.weight(.6f),textAlign=TextAlign.End,style=MaterialTheme.typography.titleMedium)
        }
    }
}
