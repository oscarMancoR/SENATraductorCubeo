package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE collection_name = :collectionName")
    suspend fun getMetadata(collectionName: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata")
    fun getAllMetadataFlow(): Flow<List<SyncMetadataEntity>>
}