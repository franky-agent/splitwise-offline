package dev.splitwise.offline.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.splitwise.offline.data.repo.GroupRepository
import dev.splitwise.offline.domain.model.BalanceCalculator
import dev.splitwise.offline.domain.model.Expense
import dev.splitwise.offline.domain.model.Group
import dev.splitwise.offline.domain.model.Member
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val repo: GroupRepository,
    private val deviceId: String,
) : ViewModel() {

    private val groupIdFlow = MutableStateFlow<String?>(null)

    val group: StateFlow<Group?> = groupIdFlow.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(null) else repo.observeGroup(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var groupId: String = ""

    fun init(groupId: String) {
        this.groupId = groupId
        groupIdFlow.value = groupId
    }

    fun addMember(name: String) {
        viewModelScope.launch {
            val memberId = "u-" + UUID.randomUUID().toString().take(8)
            repo.mutateGroup(groupId, deviceId) { g ->
                // g.clock has already been bumped by mutateGroup; reuse it for new fields.
                val ts = g.updatedAt
                val member = Member.new(memberId, name, deviceId, g.clock, ts)
                g.copy(
                    members = g.members + (memberId to member),
                    memberTags = g.memberTags.add(memberId, tag = "$deviceId:${g.clock[deviceId]}"),
                )
            }
        }
    }

    fun addExpense(
        description: String,
        amountMinor: Long,
        paidByMemberId: String,
        splitMemberIds: List<String>,
    ) {
        viewModelScope.launch {
            val expenseId = "e-" + UUID.randomUUID().toString().take(12)
            repo.mutateGroup(groupId, deviceId) { g ->
                val ts = g.updatedAt
                val splits = Expense.equalSplits(amountMinor, splitMemberIds)
                val expense = Expense.new(
                    id = expenseId,
                    description = description,
                    amount = amountMinor,
                    currency = g.currency.value,
                    paidBy = paidByMemberId,
                    splits = splits,
                    deviceId = deviceId,
                    clock = g.clock,
                    ts = ts,
                )
                g.copy(expenses = g.expenses + (expenseId to expense))
            }
        }
    }

    fun balances(g: Group) = BalanceCalculator.balances(g)
    fun settlements(g: Group) = BalanceCalculator.suggestSettlements(g)
    fun totalSpent(g: Group) = BalanceCalculator.totalSpent(g)
}