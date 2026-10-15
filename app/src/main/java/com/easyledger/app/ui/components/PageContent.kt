package com.easyledger.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PageContent(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, backEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=6.dp).heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically) {
            if(onBack!=null) IconButton(onBack,enabled=backEnabled,modifier=Modifier.size(48.dp)) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回上一级")}
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium,textAlign=if(onBack!=null) TextAlign.Center else TextAlign.Start)
            if(onBack!=null) Spacer(Modifier.width(48.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=4.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable
fun InformationCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal=16.dp,vertical=12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
