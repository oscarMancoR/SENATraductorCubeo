package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "palabras",
    indices = [
        Index(value = ["palabra_espanol"]),
        Index(value = ["palabra_pamiwa"])
    ]
)
data class PalabraEntity(
    @PrimaryKey val id: String,
    val palabra_espanol: String,
    val palabra_pamiwa: String,
    val significado: String?,
    val tipo_palabra: String?,
    val categoria: String?,
    val frecuencia: Int = 0,
    val confianza: Float = 1.0f,
    val expert_validated: Boolean = true,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)
