package dev.splitwise.offline.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.splitwise.offline.sync.RelayConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val relayConfig: RelayConfig,
) : ViewModel() {

    private val _url = MutableStateFlow(relayConfig.relayUrl)
    val url: StateFlow<String> = _url.asStateFlow()

    fun updateUrl(value: String) {
        _url.value = value
        relayConfig.relayUrl = value.trim()
    }
}