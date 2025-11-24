package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/*
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
)*/
@Entity(
    tableName = "oraciones",
    indices = [
        Index(value = ["espanol_presente"]),
        Index(value = ["pamie_presente"]),
        Index(value = ["familia"]),
        Index(value = ["id_base"])
    ]
)
data class OracionEntity(
    @PrimaryKey val id: String,
    val id_base: String,
    val familia: String,
    val genero: String = "neutro",
    val activo: Boolean = true,

    // Variaciones temporales - PRESENTE
    val espanol_presente: String,
    val pamie_presente: String,

    // Variaciones temporales - PASADO
    val espanol_pasado: String = "",
    val pamie_pasado: String = "",

    // Variaciones temporales - FUTURO
    val espanol_futuro: String = "",
    val pamie_futuro: String = "",

    // Metadata
    val palabras_clave: String = "", // JSON string de array
    val variaciones_disponibles: String = "", // JSON string de array
    val total_variaciones: Int = 1,
    val fuente: String = "",
    val confianza: Float = 1.0f,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)