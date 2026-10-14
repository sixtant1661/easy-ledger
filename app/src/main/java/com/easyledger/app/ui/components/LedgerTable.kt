package com.easyledger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One row per record. Long time segments wrap inside one cell; narrow screens scroll horizontally. */
@Composable
fun LedgerTable(headers: List<String>, rows: List<List<String>>, onRow: ((Int) -> Unit)? = null, total: String? = null) {
    val widths = if(headers.size==5) listOf(68,144,68,78,100) else List(headers.size) { 94 }
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(Color(0xFFEAF4F1))) {
            headers.forEachIndexed { i,text -> Text(text,Modifier.width(widths[i].dp).padding(8.dp),fontSize=16.sp,textAlign=if(i>=2) TextAlign.End else TextAlign.Start) }
        }
        if(rows.isEmpty()) Text("暂无工作记录",Modifier.padding(16.dp))
        rows.forEachIndexed { index,cells ->
            Row(Modifier.width(widths.sum().dp).then(if(onRow!=null) Modifier.clickable { onRow(index) } else Modifier).heightIn(min=52.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                cells.forEachIndexed { i,text -> Text(text.ifBlank { "—" },Modifier.width(widths[i].dp).padding(8.dp),fontSize=16.sp,lineHeight=22.sp,textAlign=if(i>=2) TextAlign.End else TextAlign.Start) }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline)
        }
        if(total!=null) Row(Modifier.width(widths.sum().dp).background(Color(0xFFFFF5DC)).padding(12.dp)) { Text("合计",Modifier.weight(1f));Text(total,style=MaterialTheme.typography.titleMedium) }
    }
}
