package dev.splitwise.offline.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.splitwise.offline.sync.CompositeSyncTransport
import dev.splitwise.offline.sync.SyncTransport
import javax.inject.Singleton

/**
 * Binds the [SyncTransport] interface to the [CompositeSyncTransport], which itself
 * composes [dev.splitwise.offline.sync.NearbyConnectionsTransport] (proximity P2P,
 * no network) and [dev.splitwise.offline.sync.RelaySyncTransport] (remote sync via
 * the minimal storage-free relay).
 *
 * This is what makes the transport pluggable: to add a third transport (e.g. a
 * Google-Drive-file transport), implement `SyncTransport`, inject it into
 * `CompositeSyncTransport`, and nothing else in the app changes — the data layer
 * and UI keep depending on the single `SyncTransport` interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindSyncTransport(impl: CompositeSyncTransport): SyncTransport
}