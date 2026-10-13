package dev.splitwise.offline.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(
    onCreated: (String) -> Unit,
    vm: GroupListViewModel = hiltViewModel(),
) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("USD") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("New group") }) }
    ) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.uppercase().take(3) },
                label = { Text("Currency") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val g = vm.createGroupBlocking(name.trim(), currency.trim())
                            onCreated(g.id)
                        }
                    }
                },
                enabled = name.isNotBlank() && currency.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create") }
            TextButton(onClick = { onCreated("") }) { Text("Cancel") }
        }
    }
}