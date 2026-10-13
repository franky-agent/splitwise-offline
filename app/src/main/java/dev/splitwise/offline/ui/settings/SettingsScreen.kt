package dev.splitwise.offline.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val url by vm.url.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Remote sync relay",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "For syncing when devices are NOT near each other. Leave blank to use " +
                    "nearby-only (proximity) sync. The relay is a tiny storage-free message " +
                    "router — it forwards bytes between devices and never stores your data.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = url,
                onValueChange = vm::updateUrl,
                label = { Text("Relay WebSocket URL") },
                placeholder = { Text("wss://relay.example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                "Example: wss://my-relay.fly.dev  (or ws://10.0.0.5:8787 on your LAN).\n" +
                    "Run your own from the relay/ folder in the repo: node relay/server.js",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}