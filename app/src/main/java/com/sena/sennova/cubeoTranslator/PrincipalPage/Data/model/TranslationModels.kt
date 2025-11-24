package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName

enum class TranslationDirection {
    ES_TO_PAMIWA,
    PAMIWA_TO_ES
}

enum class TranslationMethod {
    COINCIDENCIA_EXACTA,        // Coincidencia exacta en corpus
    ORACION_SIMILAR,   // Oración similar encontrada
    PALABRA_POR_PALABRA,      // Traducción palabra por palabra
    HYBRID_AI,         // IA con contexto del corpus
    USER_CORRECTION,   // Corrección previa del usuario
    CONTEXTUAL_SEARCH  // Búsqueda contextual en corpus
}

enum class TranslationMode {
    NATURAL,    // Traducción natural/contextual
    LITERAL,    // Palabra por palabra
    BOTH        // Muestra ambos modos
}

enum class ValidationStatus {
    PENDING,    // Pendiente revisión del experto
    APPROVED,   // Aprobada por experto → mover a corpus oficial
    REJECTED,   // Rechazada → no usar más
    EDITED      // Experto la editó → usar versión del experto
}

// Modelo para palabras individuales
@Entity(tableName = "expert_words")
data class WordModel(
    val id: String = "",
    val palabraEspanol: String = "",
    val palabraPamie: String = "",  // 🔴 CAMBIADO de palabraPamiwa
    val significado: String = "",
    val tipoPalabra: String = "",
    val activo: Boolean = true,
    val fuente: String = "",
    val confianza: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
)

// Modelo para oraciones completas
@Entity(tableName = "expert_sentences")
data class SentenceModel(
    val id: String = "",
    val idBase: String = "",
    val familia: String = "",
    val genero: String = "neutro",
    val activo: Boolean = true,

    // Oración principal (según tiempo seleccionado)
    val oracionEspanol: String = "",
    val oracionPamie: String = "",  //

    // Todas las variaciones temporales
    val espanolPresente: String = "",
    val pamiePresente: String = "",
    val espanolPasado: String = "",
    val pamiePasado: String = "",
    val espanolFuturo: String = "",
    val pamieFuturo: String = "",

    // Metadata
    val palabrasClave: List<String> = emptyList(),
    val variacionesDisponibles: List<String> = emptyList(),
    val totalVariaciones: Int = 1,
    val fuente: String = "",
    val confianza: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),

    // Para búsqueda por similitud
    val similarity: Float = 0f
) {
    // Propiedades de compatibilidad (para código existente que use oracionPamiwa)
    val oracionPamiwa: String get() = oracionPamie
}

// Modelo para correcciones del usuario (SISTEMA HÍBRIDO)
@Entity(tableName = "user_corrections")
data class UserCorrectionModel(
    @PrimaryKey val id: String = "",
    val textoOriginal: String = "",
    val traduccionIA: String = "",
    val correccionUsuario: String = "",
    val direccion: TranslationDirection = TranslationDirection.ES_TO_PAMIWA,
    val metodoOriginal: TranslationMethod = TranslationMethod.PALABRA_POR_PALABRA,
    val confianzaOriginal: Float = 0f,

    // CAMPOS PARA VALIDACIÓN HÍBRIDA
    val aplicadaInmediatamente: Boolean = true,  // Usar mientras se valida
    val validadaPorExperto: Boolean = false,     // Revisada por experto
    val estadoValidacion: ValidationStatus = ValidationStatus.PENDING,
    val comentarioExperto: String = "",
    val confianzaUsuario: Float = 0.7f, // Menor que corpus del experto

    // METADATOS
    val usuarioId: String = "anonymous", // Puedes usar device ID o user ID
    val timestamp: Long = System.currentTimeMillis(),
    val numeroReportes: Int = 0 // Si otros usuarios reportan como incorrecta
)

// =============================================================================
// MODELOS DE RESPUESTA
// =============================================================================

// Desglose palabra por palabra
data class WordBreakdown(
    val palabraOriginal: String,
    val palabraTraducida: String,
    val categoria: String,
    val confianza: Float,
    val encontradaEnCorpus: Boolean
)

// Modelo de respuesta unificado
data class TranslationResponse(
    val textoOriginal: String,
    val traduccionNatural: String,
    val traduccionLiteral: String? = null,
    val direccion: TranslationDirection,
    val metodo: TranslationMethod,
    val confianza: Float,
    val tiempoRespuesta: Long = 0,

    // Información adicional
    val desglosePalabras: List<WordBreakdown> = emptyList(),
    val oracionesSimilares: List<SentenceModel> = emptyList(),
    val esDesdCache: Boolean = false,
    val esCorreccionUsuario: Boolean = false,
    val requiereValidacionExperto: Boolean = false,
    val sugerenciasCorreccion: List<String> = emptyList()
)
// =============================================================================
// ESTADOS DE UI
// =============================================================================

enum class CorpusLoadingState {
    LOADING,
    LOADED,
    ERROR,
    EMPTY
}

// Estado de UI unificado
data class TranslationUIState(
    val textoInput: String = "",
    val traduciendo: Boolean = false,
    val resultado: TranslationResponse? = null,
    val error: String? = null,
    val modo: TranslationMode = TranslationMode.NATURAL,
    val direccion: TranslationDirection = TranslationDirection.ES_TO_PAMIWA,
    val mostrarDesglose: Boolean = false,
    val mostrarSimilares: Boolean = false,
    val estadoCorpus: CorpusLoadingState = CorpusLoadingState.LOADING
)

// =============================================================================
// MODELOS AUXILIARES
// =============================================================================

data class CorpusStats(
    val totalPalabras: Int,
    val totalOraciones: Int,
    val totalCorreccionesPendientes: Int,
    val ultimaActualizacion: Long,
    val precisionPromedio: Float
)

// Para cache local (implementaremos después)
@Entity(tableName = "translation_cache")
data class TranslationCacheModel(
    @PrimaryKey val cacheKey: String = "",
    val textoOriginal: String = "",
    val textoTraducido: String = "",
    val direccion: TranslationDirection = TranslationDirection.ES_TO_PAMIWA,
    val metodo: TranslationMethod = TranslationMethod.PALABRA_POR_PALABRA,
    val confianza: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val expiraEn: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 horas
)

// =============================================================================
// FUNCIONES DE EXTENSIÓN ÚTILES
// =============================================================================

// Convertir tu modelo actual a los nuevos modelos
fun com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.WordModel.toWordBreakdown(
    palabraOriginal: String,
    esDireccionEspanolAPamiwa: Boolean
): WordBreakdown {
    return WordBreakdown(
        palabraOriginal = palabraOriginal,
        palabraTraducida = if (esDireccionEspanolAPamiwa) this.palabraPamie else this.palabraEspanol,
        categoria = this.tipoPalabra,
        confianza = this.confianza,
        encontradaEnCorpus = true
    )
}

// Crear hash para cache
fun createCacheKey(texto: String, direccion: TranslationDirection): String {
    return "${texto.lowercase().trim()}_${direccion.name}".hashCode().toString()
}

// Verificar si es una oración (tiene espacios) o palabra individual
fun String.isMultipleWords(): Boolean {
    return this.trim().contains(" ")
}

// Limpiar texto para búsqueda
fun String.cleanForSearch(): String {
    return this.lowercase()
        .trim()
        .replace(Regex("[^a-záéíóúñü\\s]"), "") // Mantener solo letras y espacios
        .replace(Regex("\\s+"), " ") // Normalizar espacios
}

// =============================================================================
// CONSTANTES
// =============================================================================

object TranslationConstants {
    const val SIMILARITY_THRESHOLD = 0.7f
    const val USER_CORRECTION_CONFIDENCE = 0.7f
    const val EXPERT_VALIDATION_CONFIDENCE = 0.95f
    const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 horas
    const val DEBOUNCE_DELAY_MS = 500L
    const val MAX_SIMILAR_SENTENCES = 5
    const val MAX_WORD_BREAKDOWN_DISPLAY = 10
}

/*
SIMILARITY_THRESHOLD: cuán parecida debe ser una frase para considerarla “similar”.
USER_CORRECTION_CONFIDENCE: confianza mínima para aceptar una corrección de usuario.
EXPERT_VALIDATION_CONFIDENCE: confianza mínima para validación de experto.
CACHE_DURATION_MS: cuánto tiempo guardar traducciones (24h).
DEBOUNCE_DELAY_MS: retardo para evitar ejecutar traducciones en cada tecla (ej. esperar 500 ms tras dejar de escribir).
MAX_SIMILAR_SENTENCES: máximo de frases similares que se mostrarán.
MAX_WORD_BREAKDOWN_DISPLAY: máximo de desgloses por palabra que se enseñan en UI.
* */