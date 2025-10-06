package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "oraciones",
    indices = [
        Index(value = ["oracion_espanol"]),
        Index(value = ["oracion_pamiwa"]),
        Index(value = ["categoria"])
    ]
)
data class OracionEntity(
    @PrimaryKey val id: String,
    val oracion_espanol: String,
    val oracion_pamiwa: String,
    val categoria: String?,
    val dificultad: String?,
    val relacion_familiar: String?,
    val genero_dirigido: String?,
    val confianza: Float = 1.0f,
    val expert_validated: Boolean = true,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)