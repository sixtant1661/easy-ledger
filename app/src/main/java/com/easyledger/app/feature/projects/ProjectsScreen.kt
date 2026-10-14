package com.easyledger.app.feature.projects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.easyledger.app.feature.ledger.LedgerViewModel
import com.easyledger.app.feature.ledger.ProjectList

@Composable
fun ProjectsScreen(model: LedgerViewModel, onCreate: () -> Unit, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    ProjectList(model, onCreate, onEdit, modifier)
}
