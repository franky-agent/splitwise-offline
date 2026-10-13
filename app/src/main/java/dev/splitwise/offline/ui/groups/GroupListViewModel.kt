package dev.splitwise.offline.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.splitwise.offline.data.repo.GroupRepository
import dev.splitwise.offline.domain.model.BalanceCalculator
import dev.splitwise.offline.domain.model.Group
import dev.splitwise.offline.sync.SyncEngine
import dev.splitwise.offline.sync.SyncTransport
import dev.splitwise.offline.sync.TransportStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupListViewModel @Inject constructor(
    private val repo: GroupRepository,
    private val syncEngine: SyncEngine,
    private val transport: SyncTransport,
    private val deviceId: String,
) : ViewModel() {

    val groups: StateFlow<List<Group>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live sync transport status for the banner in the UI. */
    val syncStatus: StateFlow<TransportStatus> = transport.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransportStatus.IDLE)

    fun createGroup(name: String, currency: String) {
        viewModelScope.launch { repo.createGroup(name, currency, deviceId) }
    }

    /** Suspend variant for screens that need the created group's id. */
    suspend fun createGroupBlocking(name: String, currency: String): dev.splitwise.offline.domain.model.Group =
        repo.createGroup(name, currency, deviceId)

    fun startSync() {
        // Start both sides so any nearby device can find us (star + cluster strategy).
        syncEngine.start(deviceId)
    }

    fun stopSync() = syncEngine.stop()

    fun summaryFor(group: Group): String {
        val balances = BalanceCalculator.balances(group)
        val owed = balances.values.sumOf { if (it > 0) it else 0L }
        return formatMinor(owed)
    }
}

/** Format minor units (cents) as a decimal string. */
fun formatMinor(minor: Long, currency: String = "USD"): String {
    val sign = if (minor < 0) "-" else ""
    val abs = Math.abs(minor)
    val major = abs / 100
    val cents = abs % 100
    return "$sign$major.${"%02d".format(cents)} $currency"
}