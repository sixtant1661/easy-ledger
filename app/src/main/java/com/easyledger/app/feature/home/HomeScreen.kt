package com.easyledger.app.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easyledger.app.R
import com.easyledger.app.feature.ledger.LedgerViewModel
import com.easyledger.app.ui.components.LedgerTable

@Composable
fun HomeScreen(onOpenProjects: () -> Unit, modifier: Modifier = Modifier, model: HomeViewModel = viewModel(), onNewRecord: () -> Unit = {}, onHistory: () -> Unit = {}, onBatch: () -> Unit = {}, onSettlement: () -> Unit = {}, onCreateProject: () -> Unit = onOpenProjects, onSettings: () -> Unit = {}, onRecord: (String) -> Unit = {}, ledger: LedgerViewModel = viewModel()) {
    val records by ledger.allRecords.collectAsStateWithLifecycle()
    Column(modifier.verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(225.dp)) {
            Image(painterResource(R.drawable.welcome_flowers),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Text("Hi，\n今天过得怎么样？",Modifier.align(Alignment.BottomStart).padding(24.dp),style=MaterialTheme.typography.headlineMedium,color=Color(0xFF124F4D))
            TextButton(onSettings,Modifier.align(Alignment.TopEnd).padding(12.dp)) { Text("⚙",fontSize=28.sp) }
        }
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Card(onClick=onNewRecord,modifier=Modifier.weight(1f).heightIn(min=236.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primary)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) { Text("▤ ＋",fontSize=48.sp,color=Color.White);Text("新增\n工作记录",style=MaterialTheme.typography.titleLarge,color=Color.White) }
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Card(onClick=onBatch,modifier=Modifier.fillMaxWidth().heightIn(min=112.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFE7F3F1))) { Column(Modifier.padding(16.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) { Text("▤",fontSize=30.sp,color=MaterialTheme.colorScheme.primary);Text("批量增加") } }
                    Card(onClick=onCreateProject,modifier=Modifier.fillMaxWidth().heightIn(min=112.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFFAF2DF))) { Column(Modifier.padding(16.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) { Text("▱",fontSize=30.sp,color=MaterialTheme.colorScheme.primary);Text("创建项目") } }
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically) { Text("最近记录",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onHistory) { Text("查看全部 ›") } }
            val recent=records.take(5)
            LedgerTable(listOf("日期","工作时长","单价","金额"),recent.map { listOf(it.record.date.substring(5).replace('-','/'),it.record.quantity+it.record.unit,it.record.unitPrice,it.record.amount) },onRow={onRecord(recent[it].record.id)})
        }
    }
}
