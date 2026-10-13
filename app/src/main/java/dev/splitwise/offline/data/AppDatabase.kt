package dev.splitwise.offline.data

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.splitwise.offline.data.dao.GroupDao
import dev.splitwise.offline.data.entity.GroupEntity

@Database(entities = [GroupEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao

    companion object {
        const val NAME = "splitwise-offline.db"
    }
}