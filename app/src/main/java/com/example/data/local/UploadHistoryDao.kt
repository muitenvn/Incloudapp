package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.UploadedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadHistoryDao {
    @Query("SELECT * FROM upload_history ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<UploadedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: UploadedItemEntity): Long

    @Query("DELETE FROM upload_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM upload_history")
    suspend fun clearAll()
}
