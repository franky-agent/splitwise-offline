package dev.splitwise.offline.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.splitwise.offline.sync.NearbyConnectionsTransport
import dev.splitwise.offline.sync.SyncTransport
import javax.inject.Singleton

/**
 * Binds the [SyncTransport] interface to its Nearby Connections implementation.
 *
 * This is what makes the transport pluggable: to swap in a different transport
 * (e.g. a Google-Drive-file or WebRTC transport), create the new class with
 * `@Inject constructor` + `@Singleton` and change only this `@Binds` method.
 * The data layer and UI never need to change.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindSyncTransport(impl: NearbyConnectionsTransport): SyncTransport
}