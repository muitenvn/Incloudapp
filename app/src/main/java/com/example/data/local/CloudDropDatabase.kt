package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.UploadedItemEntity

@Database(
    entities = [UploadedItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CloudDropDatabase : RoomDatabase() {
    abstract fun uploadHistoryDao(): UploadHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: CloudDropDatabase? = null

        fun getInstance(context: Context): CloudDropDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CloudDropDatabase::class.java,
                    "clouddrop_history.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
