package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local

import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.SentenceModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.WordModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.OracionEntity
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.PalabraEntity


// Firebase Document → Room Entity
fun Map<String, Any>.toPalabraEntity(id: String): PalabraEntity {
    return PalabraEntity(
        id = id,
        palabra_espanol = this["palabra_espanol"] as? String ?: "",
        palabra_pamiwa = this["palabra_pamiwa"] as? String
            ?: this["palabra_cubeo"] as? String ?: "", // Compatibilidad
        significado = this["significado"] as? String,
        tipo_palabra = this["tipo_palabra"] as? String,
        categoria = this["categoria"] as? String,
        frecuencia = (this["frecuencia"] as? Number)?.toInt() ?: 0,
        confianza = (this["confianza"] as? Number)?.toFloat() ?: 1.0f,
        expert_validated = this["expert_validated"] as? Boolean ?: true,
        created_at = (this["created_at"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updated_at = System.currentTimeMillis()
    )
}

fun Map<String, Any>.toOracionEntity(id: String): OracionEntity {
    return OracionEntity(
        id = id,
        oracion_espanol = this["oracion_espanol"] as? String ?: "",
        oracion_pamiwa = this["oracion_pamiwa"] as? String
            ?: this["oracion_cubeo"] as? String ?: "", // Compatibilidad
        categoria = this["categoria"] as? String,
        dificultad = this["dificultad"] as? String,
        relacion_familiar = this["relacion_familiar"] as? String,
        genero_dirigido = this["genero_dirigido"] as? String,
        confianza = (this["confianza"] as? Number)?.toFloat() ?: 1.0f,
        expert_validated = this["expert_validated"] as? Boolean ?: true,
        created_at = (this["created_at"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updated_at = System.currentTimeMillis()
    )
}

// Room Entity → Domain Model
fun PalabraEntity.toWordModel(): WordModel {
    return WordModel(
        id = this.id,
        palabraEspanol = this.palabra_espanol,
        palabraPamiwa = this.palabra_pamiwa,
        categoria = this.categoria ?: "",
        frecuencia = this.frecuencia,
        confianza = this.confianza,
        expertValidated = this.expert_validated,
        createdAt = this.created_at
    )
}

fun OracionEntity.toSentenceModel(): SentenceModel {
    return SentenceModel(
        id = this.id,
        oracionEspanol = this.oracion_espanol,
        oracionPamiwa = this.oracion_pamiwa,
        dificultad = this.dificultad ?: "medium",
        categoria = this.categoria ?: "",
        tags = emptyList(), // Agregar después si necesitas
        confianza = this.confianza,
        expertValidated = this.expert_validated,
        createdAt = this.created_at
    )
}