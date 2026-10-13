package dev.splitwise.offline

import android.content.Context
import android.provider.Settings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.splitwise.offline.data.AppDatabase
import dev.splitwise.offline.data.dao.GroupDao
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.inject.Singleton

/**
 * Hilt DI root. Binds the JSON serializer, Room DB + DAO, the repository, and a
 * stable device id used as our CRDT replica identity.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        androidx.room.Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun groupDao(db: AppDatabase): GroupDao = db.groupDao()

    // GroupRepository is provided via its own @Inject constructor + @Singleton —
    // no @Provides needed (that would be a duplicate binding).

    /**
     * A stable per-install device id. We use Android's ANDROID_ID (per-app-signing-key
     * stable, survives reinstall on most OEMs) and generate a random UUID fallback.
     * This is our CRDT replica id — every vector-clock component is keyed on it.
     */
    @Provides @Singleton
    fun deviceId(@ApplicationContext ctx: Context): String {
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        return if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            "dev-$androidId"
        } else {
            "dev-" + ByteArray(8).let { SecureRandom().nextBytes(it); it.joinToString("") { b -> "%02x".format(b) } }
        }
    }
}