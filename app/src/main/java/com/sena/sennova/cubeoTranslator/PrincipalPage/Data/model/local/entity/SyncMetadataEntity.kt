package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "sync_metadata",
    primaryKeys = ["collection_name"]
)
data class SyncMetadataEntity(
    val collection_name: String, // "palabras" o "oraciones"
    val last_sync_timestamp: Long,
    val total_records: Int,
    val sync_status: String // "completed", "in_progress", "error"
)
