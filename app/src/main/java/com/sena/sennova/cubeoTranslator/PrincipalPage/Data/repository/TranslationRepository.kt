package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository

import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.*
import kotlinx.coroutines.flow.Flow

// Interface principal para el sistema de traducción
interface TranslationRepository {
    // Función para traducir un texto o palabra
    /**
     * Méto do principal de traducción que maneja:
     * 1. Búsqueda en corpus de oraciones
     * 2. Fallback a tu sistema actual (palabra por palabra)
     * 3. Correcciones de usuario
     * 4. Cache inteligente
     */
    suspend fun translateText(
        texto: String,
        direccion: TranslationDirection,
        modo: TranslationMode = TranslationMode.NATURAL
    ): Result<TranslationResponse>


    // Traducción específica para palabras individuales

    suspend fun translateWord(
        palabra: String,
        direccion: TranslationDirection
    ): Result<WordBreakdown>

    //Traducción específica para oraciones completas
    suspend fun translateSentence(
        oracion: String,
        direccion: TranslationDirection
    ): Result<TranslationResponse>

    // Carga el corpus desde Firebase
    suspend fun loadCorpusFromFirebase(): Result<Boolean>

    //obtiene estadisticas de traducciones
    suspend fun getCorpusStats(): CorpusStats

    // Estado de carga del corpus (para mostrar en UI)
    fun getCorpusLoadingState(): Flow<CorpusLoadingState>

    // Verifica si el corpus está listo para usar
    suspend fun isCorpusReady(): Boolean


    // =============================================================================
    // BÚSQUEDAS ESPECÍFICAS
    // =============================================================================

    //Busca coincidencia exacta en el corpus de oraciones

    suspend fun findExactMatch(
        texto: String,
        direccion: TranslationDirection
    ): SentenceModel?

    //Busca oraciones similares usando algoritmo de similitud
    suspend fun findSimilarSentences(
        texto: String,
        direccion: TranslationDirection,
        limit: Int = 5
    ): List<SentenceModel>

    //Traduce palabra por palabra
    suspend fun translateWordByWord(
        texto: String,
        direccion: TranslationDirection
    ): List<WordBreakdown>

    //Busca palabra individual en tu corpus actual
    suspend fun findWordInCorpus(
        palabra: String,
        direccion: TranslationDirection
    ): WordModel?

    // =============================================================================
    // SISTEMA DE CORRECCIONES (HÍBRIDO)
    // =============================================================================

    // Guarda corrección del usuario
    //Se aplica inmediatamente pero queda pendiente de validación del experto
    suspend fun saveUserCorrection(
        correction: UserCorrectionModel
    ): Result<Boolean>

    //Busca si existe corrección previa del usuario para este texto
    suspend fun getUserCorrection(
        texto: String,
        direccion: TranslationDirection
    ): UserCorrectionModel?

    //Obtiene correcciones pendientes de validación del experto
    suspend fun getPendingCorrections(): List<UserCorrectionModel>

    //  Aplica correcciones que ya fueron validadas por el experto
    //  Para uso futuro con dashboard del experto
    suspend fun applyValidatedCorrections(): Result<Int>

    //Marca una corrección como validada por el experto
    suspend fun markCorrectionAsValidated(
        correctionId: String,
        status: ValidationStatus,
        expertComment: String = ""
    ): Result<Boolean>

    // =============================================================================
    // CACHE Y PERFORMANCE
    // =============================================================================

    //Busca traducción en cache local
    suspend fun getCachedTranslation(
        texto: String,
        direccion: TranslationDirection
    ): TranslationCacheModel?

    //Guarda traducción en cache para futuras consultas
    suspend fun cacheTranslation(
        cache: TranslationCacheModel
    ): Result<Boolean>

    //Limpia cache expirado
    suspend fun clearExpiredCache(): Result<Int>

    //Obtiene tamaño actual del cache
    suspend fun getCacheSize(): Int

    // =============================================================================
    // MÉTODOS DE COMPATIBILIDAD
    // =============================================================================


    // Méto do compatible con tu buscarTraducciones actual
    suspend fun buscarTraduccionesCompatible(
        oracion: String
    ): List<String>


    //Busca en tu colección actual "tu_coleccion"
    suspend fun buscarEnColeccionActual(
        palabra: String
    ): String?


    // =============================================================================
    // REPORTES Y ANALYTICS
    // =============================================================================


    // Obtiene métricas de uso del traductor
    suspend fun getUsageMetrics(): TranslationMetrics


    // Reporta error en traducción
    suspend fun reportTranslationError(
        originalText: String,
        translation: String,
        errorType: String
    ): Result<Boolean>

}

    // =============================================================================
    // MODELOS AUXILIARES PARA EL REPOSITORY
    // =============================================================================

    /**
     * Métricas de uso del traductor
     */
    data class TranslationMetrics(
        val totalTranslations: Int,
        val exactMatches: Int,
        val similarMatches: Int,
        val wordByWordTranslations: Int,
        val userCorrections: Int,
        val averageConfidence: Float,
        val averageResponseTime: Long,
        val mostTranslatedWords: List<String>,
        val mostTranslatedSentences: List<String>
    )

    /**
     * Configuración del sistema de traducción
     */
    data class TranslationConfig(
        val similarityThreshold: Float = TranslationConstants.SIMILARITY_THRESHOLD,
        val cacheEnabled: Boolean = true,
        val userCorrectionsEnabled: Boolean = true,
        val expertValidationRequired: Boolean = true,
        val maxSimilarSentences: Int = TranslationConstants.MAX_SIMILAR_SENTENCES,
        val debounceDelayMs: Long = TranslationConstants.DEBOUNCE_DELAY_MS
    )

    /**
     * Estrategia de traducción basada en el input
     */
    enum class TranslationStrategy {
        EXACT_CORPUS_MATCH,    // Buscar primero en corpus de oraciones
        SIMILARITY_SEARCH,     // Buscar oraciones similares
        WORD_BY_WORD_FALLBACK, // Tu método actual como fallback
        USER_CORRECTION_FIRST, // Priorizar correcciones de usuario
        CACHE_FIRST           // Revisar cache primero
    }

    /**
     * Resultado de búsqueda en corpus
     */
    sealed class CorpusSearchResult {
        object NotFound : CorpusSearchResult()
        data class ExactMatch(val sentence: SentenceModel) : CorpusSearchResult()
        data class SimilarMatches(val sentences: List<SentenceModel>) : CorpusSearchResult()
        data class WordMatch(val word: WordModel) : CorpusSearchResult()
    }

    /**
     * Request para traducción con contexto adicional
     */
    data class TranslationRequest(
        val texto: String,
        val direccion: TranslationDirection,
        val modo: TranslationMode = TranslationMode.NATURAL,
        val strategy: TranslationStrategy? = null,
        val config: TranslationConfig = TranslationConfig(),
        val contextualInfo: Map<String, Any> = emptyMap()
    )
    // =============================================================================
    // EXTENSIONES ÚTILES PARA EL REPOSITORY
    // =============================================================================

    /**
     * Extension para convertir resultado de traducción a formato compatible
     */
    fun TranslationResponse.toCompatibleList(): List<String> {
        return if (this.traduccionLiteral != null && this.traduccionLiteral != this.traduccionNatural) {
            listOf(this.traduccionNatural, this.traduccionLiteral)
        } else {
            listOf(this.traduccionNatural)
        }
    }

    /**
     * Extension para crear request básico
     */
    fun String.toTranslationRequest(
        direccion: TranslationDirection,
        modo: TranslationMode = TranslationMode.NATURAL
    ): TranslationRequest {
        return TranslationRequest(
            texto = this,
            direccion = direccion,
            modo = modo
        )
    }

    /**
     * Extension para verificar si el texto requiere traducción de oración vs palabra
     */
    fun String.getRecommendedStrategy(): TranslationStrategy {
        return when {
            this.isMultipleWords() -> TranslationStrategy.EXACT_CORPUS_MATCH
            else -> TranslationStrategy.WORD_BY_WORD_FALLBACK
        }
    }

    /**
     * Extension para crear cache key
     */
    fun TranslationRequest.toCacheKey(): String {
        return createCacheKey(this.texto, this.direccion)
    }

// =============================================================================
// INTERFACES ADICIONALES (Para futuras extensiones)
// =============================================================================

    /**
     * Interface para observar cambios en el corpus
     */
    interface CorpusObserver {
        fun onCorpusLoaded(stats: CorpusStats)
        fun onCorpusError(error: Exception)
        fun onNewCorrection(correction: UserCorrectionModel)
    }

    /**
     * Interface para validación de correcciones por experto
     */
    interface ExpertValidationInterface {
        suspend fun submitForValidation(correction: UserCorrectionModel): Result<Boolean>
        suspend fun getValidationStatus(correctionId: String): ValidationStatus
        suspend fun processExpertFeedback(
            correctionId: String,
            status: ValidationStatus,
            feedback: String
        ): Result<Boolean>
    }

    /**
     * Interface para analytics y métricas
     */
    interface TranslationAnalytics {
        suspend fun trackTranslation(request: TranslationRequest, response: TranslationResponse)
        suspend fun trackUserCorrection(correction: UserCorrectionModel)
        suspend fun trackError(error: Exception, context: String)
        suspend fun getInsights(): TranslationInsights
    }

    data class TranslationInsights(
        val popularTranslations: List<String>,
        val commonErrors: List<String>,
        val improvementSuggestions: List<String>,
        val accuracyTrends: Map<String, Float>
    )






