package dev.splitwise.offline.ui.expenses

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.splitwise.offline.ui.groups.formatMinor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String,
    onDone: () -> Unit,
    vm: AddExpenseViewModel = hiltViewModel(),
) {
    LaunchedEffect(groupId) { vm.init(groupId) }
    val group by vm.group.collectAsStateWithLifecycle()

    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf<String?>(null) }
    var splitAmong by remember { mutableStateOf<Set<String>>(emptySet()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Add expense") })
        }
    ) { padding ->
        val g = group ?: return@Scaffold
        val memberIds = g.memberIds
        LaunchedEffect(memberIds) {
            if (paidBy == null && memberIds.isNotEmpty()) paidBy = memberIds.first()
            splitAmong = splitAmong.ifEmpty { memberIds.toSet() }
        }

        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Amount (${g.currency.value})") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("Paid by", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(memberIds, key = { it }) { id ->
                    val name = g.members[id]?.displayName ?: id
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = paidBy == id, onCheckedChange = { if (it) paidBy = id })
                        Text(name)
                    }
                }
            }

            Text("Split among", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(memberIds, key = { it }) { id ->
                    val name = g.members[id]?.displayName ?: id
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = id in splitAmong,
                            onCheckedChange = { checked ->
                                splitAmong = if (checked) splitAmong + id else splitAmong - id
                            }
                        )
                        Text(name)
                    }
                }
            }

            val amountMinor = parseAmountMinor(amountText)
            val canSubmit = description.isNotBlank() && amountMinor > 0 && paidBy != null && splitAmong.isNotEmpty()
            Button(
                onClick = { vm.addExpense(description.trim(), amountMinor, paidBy!!, splitAmong.toList(), onDone) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save expense")
                if (amountMinor > 0) Text("  ·  ${formatMinor(amountMinor, g.currency.value)}")
            }
        }
    }
}

/** "12.34" -> 1234L (minor units). Tolerant of empty / partial input. */
private fun parseAmountMinor(text: String): Long {
    val cleaned = text.trim().ifEmpty { return 0L }
    val parts = cleaned.split('.')
    val major = parts.getOrNull(0)?.toLongOrNull() ?: return 0L
    val cents = parts.getOrNull(1)?.let { (it + "00").take(2).toLong() } ?: 0L
    return major * 100 + cents
}