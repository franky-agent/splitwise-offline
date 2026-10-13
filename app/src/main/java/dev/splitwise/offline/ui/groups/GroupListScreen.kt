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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.splitwise.offline.domain.model.Group
import dev.splitwise.offline.sync.TransportStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    onNewGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: GroupListViewModel = hiltViewModel(),
) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Splitwise Offline") },
                actions = {
                    IconButton(onClick = { vm.startSync() }) {
                        Icon(Icons.Filled.Sync, contentDescription = "Sync")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewGroup) {
                Icon(Icons.Filled.Add, contentDescription = "New group")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SyncStatusBanner(status = syncStatus)
            if (groups.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No groups yet.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap + to create a group, then tap the sync icon. Devices nearby " +
                            "sync over Wi-Fi Direct/BLE; for remote sync, set a relay URL in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(groups, key = { it.id }) { g ->
                        GroupCard(g, vm.summaryFor(g), onClick = { onOpenGroup(g.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusBanner(status: TransportStatus) {
    val (text, color) = when (status) {
        TransportStatus.IDLE -> null to null
        TransportStatus.SEARCHING -> "Searching for sync peers…" to MaterialTheme.colorScheme.tertiaryContainer
        TransportStatus.CONNECTED -> "Sync connected" to MaterialTheme.colorScheme.primaryContainer
        TransportStatus.ERROR -> "Sync error — check relay URL in Settings" to MaterialTheme.colorScheme.errorContainer
    }
    if (text == null) return
    Surface(color = color!!, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun GroupCard(group: Group, summary: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(group.displayName, style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${group.memberIds.size} members", style = MaterialTheme.typography.bodySmall)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}