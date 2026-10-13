package dev.splitwise.offline.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.splitwise.offline.domain.model.Group

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onAddExpense: () -> Unit,
    onBack: () -> Unit,
    vm: GroupDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(groupId) { vm.init(groupId) }
    val group by vm.group.collectAsStateWithLifecycle()
    var showAddMember by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.displayName ?: "Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddMember = true }) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "Add member")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense, enabled = (group?.memberIds?.size ?: 0) > 0) {
                Icon(Icons.Filled.Add, contentDescription = "Add expense")
            }
        }
    ) { padding ->
        val g = group
        if (g == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Loading…")
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Balances section
            item {
                SectionTitle("Balances")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val balances = vm.balances(g)
                        if (g.memberIds.isEmpty()) {
                            Text("No members yet. Tap the person icon to add one.")
                        } else {
                            g.memberIds.forEach { id ->
                                val m = g.members[id]
                                val bal = balances[id] ?: 0L
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(m?.displayName ?: id)
                                    Text(
                                        formatMinor(bal, g.currency.value),
                                        color = if (bal >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            Text(
                                "Total spent: ${formatMinor(vm.totalSpent(g), g.currency.value)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            // Settlement suggestions
            val settlements = vm.settlements(g)
            if (settlements.isNotEmpty()) {
                item {
                    SectionTitle("Suggested settlements")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            settlements.forEach { s ->
                                Text("${g.members[s.from]?.displayName ?: s.from} pays ${g.members[s.to]?.displayName ?: s.to} ${formatMinor(s.amount, g.currency.value)}")
                            }
                        }
                    }
                }
            }

            // Expenses
            item { SectionTitle("Expenses") }
            val expenses = g.activeExpenses.sortedByDescending { it.createdAt }
            if (expenses.isEmpty()) {
                item { Text("No expenses yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(expenses, key = { it.id }) { e ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(e.description.value, style = MaterialTheme.typography.titleMedium)
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Paid by ${g.members[e.paidBy.value]?.displayName ?: e.paidBy.value}", style = MaterialTheme.typography.bodySmall)
                                Text(formatMinor(e.amount.value, e.currency.value), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMember) {
        AddMemberDialog(onAdd = { name -> vm.addMember(name); showAddMember = false }, onDismiss = { showAddMember = false })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun AddMemberDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add member") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onAdd(name.trim()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}