package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local

import com.google.gson.Gson
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.SentenceModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.WordModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.OracionEntity
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.PalabraEntity


// Firebase Document → Room Entity
private val gson = Gson()

// =============================================================================
// FIREBASE → ROOM ENTITY
// =============================================================================

/**
 * Mapea documento de Firebase (colección "palabras") a PalabraEntity
 */
fun Map<String, Any>.toPalabraEntity(id: String): PalabraEntity {
    return PalabraEntity(
        id = id,
        palabra_espanol = (this["palabra_español"] as? String
            ?: this["palabra_espanol"] as? String ?: "").lowercase().trim(),
        palabra_pamie = (this["palabra_pamie"] as? String ?: "").lowercase().trim(),
        significado = this["significado"] as? String,
        tipo_palabra = this["tipo_palabra"] as? String,
        activo = this["activo"] as? Boolean ?: true,
        fuente = this["fuente"] as? String,
        confianza = (this["confianza"] as? Number)?.toFloat() ?: 1.0f,
        created_at = extractTimestamp(this["fecha_creacion"]),
        updated_at = System.currentTimeMillis()
    )
}

/**
 * Mapea documento de Firebase (colección "oraciones_completas") a OracionEntity
 */
@Suppress("UNCHECKED_CAST")
fun Map<String, Any>.toOracionEntity(id: String): OracionEntity {
    // Extraer variaciones anidadas
    val variaciones = this["variaciones"] as? Map<String, Any> ?: emptyMap()
    val tiempos = variaciones["tiempos"] as? Map<String, Any> ?: emptyMap()

    val presente = tiempos["presente"] as? Map<String, Any> ?: emptyMap()
    val pasado = tiempos["pasado"] as? Map<String, Any> ?: emptyMap()
    val futuro = tiempos["futuro"] as? Map<String, Any> ?: emptyMap()

    // Extraer arrays
    val palabrasClave = this["palabras_clave"] as? List<String> ?: emptyList()
    val variacionesDisp = this["variaciones_disponibles"] as? List<String> ?: emptyList()

    return OracionEntity(
        id = id,
        id_base = this["id_base"] as? String ?: id,
        familia = this["familia"] as? String ?: "",
        genero = this["genero"] as? String ?: "neutro",
        activo = this["activo"] as? Boolean ?: true,

        // Presente
        espanol_presente = (presente["español"] as? String
            ?: presente["espanol"] as? String ?: "").trim(),
        pamie_presente = (presente["pamie"] as? String ?: "").trim(),

        // Pasado
        espanol_pasado = (pasado["español"] as? String
            ?: pasado["espanol"] as? String ?: "").trim(),
        pamie_pasado = (pasado["pamie"] as? String ?: "").trim(),

        // Futuro
        espanol_futuro = (futuro["español"] as? String
            ?: futuro["espanol"] as? String ?: "").trim(),
        pamie_futuro = (futuro["pamie"] as? String ?: "").trim(),

        // Metadata
        palabras_clave = gson.toJson(palabrasClave),
        variaciones_disponibles = gson.toJson(variacionesDisp),
        total_variaciones = (this["total_variaciones"] as? Number)?.toInt() ?: 1,
        fuente = this["fuente"] as? String ?: "",
        confianza = (this["confianza"] as? Number)?.toFloat() ?: 1.0f,
        created_at = extractTimestamp(this["fecha_creacion"]),
        updated_at = System.currentTimeMillis()
    )
}

/**
 * Extrae timestamp de diferentes formatos de Firebase
 */
private fun extractTimestamp(value: Any?): Long {
    return when (value) {
        is Long -> value
        is Number -> value.toLong()
        is com.google.firebase.Timestamp -> value.toDate().time
        else -> System.currentTimeMillis()
    }
}

// =============================================================================
// ROOM ENTITY → DOMAIN MODEL
// =============================================================================

/**
 * Convierte PalabraEntity a WordModel
 */
fun PalabraEntity.toWordModel(): WordModel {
    return WordModel(
        id = this.id,
        palabraEspanol = this.palabra_espanol,
        palabraPamie = this.palabra_pamie,
        significado = this.significado ?: "",
        tipoPalabra = this.tipo_palabra ?: "",
        activo = this.activo,
        fuente = this.fuente ?: "",
        confianza = this.confianza,
        createdAt = this.created_at
    )
}

/**
 * Convierte OracionEntity a SentenceModel
 * Por defecto usa la variación "presente"
 */
fun OracionEntity.toSentenceModel(tiempo: String = "presente"): SentenceModel {
    val (espanol, pamie) = when (tiempo.lowercase()) {
        "pasado" -> Pair(this.espanol_pasado, this.pamie_pasado)
        "futuro" -> Pair(this.espanol_futuro, this.pamie_futuro)
        else -> Pair(this.espanol_presente, this.pamie_presente)
    }

    return SentenceModel(
        id = this.id,
        idBase = this.id_base,
        familia = this.familia,
        genero = this.genero,
        activo = this.activo,
        oracionEspanol = espanol,
        oracionPamie = pamie,

        // Todas las variaciones
        espanolPresente = this.espanol_presente,
        pamiePresente = this.pamie_presente,
        espanolPasado = this.espanol_pasado,
        pamiePasado = this.pamie_pasado,
        espanolFuturo = this.espanol_futuro,
        pamieFuturo = this.pamie_futuro,

        palabrasClave = try {
            gson.fromJson(this.palabras_clave, Array<String>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        },
        variacionesDisponibles = try {
            gson.fromJson(this.variaciones_disponibles, Array<String>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        },
        totalVariaciones = this.total_variaciones,
        fuente = this.fuente,
        confianza = this.confianza,
        createdAt = this.created_at
    )
}