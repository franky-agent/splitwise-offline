package dev.splitwise.offline.sync

import javax.inject.Qualifier

/**
 * Qualifies a long-lived [kotlinx.coroutines.CoroutineScope] tied to the app process.
 * Used by [CompositeSyncTransport] to merge child-transport flows for the transport's
 * lifetime. Provided in [dev.splitwise.offline.di.AppModule].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope