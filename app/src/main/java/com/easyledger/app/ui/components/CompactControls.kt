package com.easyledger.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FoldHeader(title: String, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min=48.dp).semantics {stateDescription=if(expanded) "已展开" else "已折叠"}.clickable(role=Role.Button,onClick=onClick).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface)
        Icon(if(expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,if(expanded) "收起" else "展开",Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SolidSelector(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, maxLines: Int = 1) {
    Button(onClick,modifier.heightIn(min=52.dp),enabled=enabled,shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=10.dp)) {
        Text(text,Modifier.weight(1f),fontSize=16.sp,lineHeight=22.sp,maxLines=maxLines,overflow=if(maxLines==1) TextOverflow.Ellipsis else TextOverflow.Clip)
        Icon(Icons.Default.KeyboardArrowDown,null,Modifier.size(18.dp))
    }
}
