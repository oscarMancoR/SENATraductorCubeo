package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ColumnInfo

@Entity(
    tableName = "cache_api_traducciones",
    indices = [
        Index(value = ["texto_original", "direccion"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class CacheTraduccionApiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "texto_original")
    val textoOriginal: String,

    @ColumnInfo(name = "traduccion")
    val traduccion: String,

    @ColumnInfo(name = "direccion")
    val direccion: String, // "ES_TO_PAMIWA" o "PAMIWA_TO_ES"

    @ColumnInfo(name = "es_palabra")
    val esPalabra: Boolean,

    @ColumnInfo(name = "confianza")
    val confianza: Float = 0.8f,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "origen")
    val origen: String = "API_MBART", // "API_MBART", "ROOM_EXACTA", "ROOM_SIMILAR"

    // Para expiración de caché
    @ColumnInfo(name = "expira_en")
    val expiraEn: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // 30 días
)
