package dev.splitwise.offline.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted configuration for the relay transport. Just the WebSocket URL the user
 * entered in Settings — nothing else. Stored in SharedPreferences so it survives
 * restarts. The relay itself stores nothing; this is the only place the URL lives.
 *
 * Set to null/blank to disable the relay transport and rely on Nearby-only sync.
 */
@Singleton
class RelayConfig @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** WebSocket URL of the relay, e.g. "ws://10.0.0.5:8787" or "wss://relay.example.com". Blank = disabled. */
    var relayUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_URL, value).apply() }

    val isEnabled: Boolean get() = relayUrl.isNotBlank()

    companion object {
        private const val PREFS = "relay_config"
        private const val KEY_URL = "url"
    }
}