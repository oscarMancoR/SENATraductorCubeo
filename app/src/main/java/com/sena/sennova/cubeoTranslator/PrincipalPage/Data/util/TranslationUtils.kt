package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util

import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.SentenceModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.TranslationDirection
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.TranslationMethod
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.TranslationResponse
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.WordBreakdown
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.TranslationStrategy
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.TextUtils.getTextType
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.TextUtils.isGreeting
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.TextUtils.isQuestion
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.TextUtils.normalizeForSearch

/**
 * Utilidades específicas para el sistema de traducción
 */
object TranslationUtils {

    /**
     * Determina la estrategia de traducción más apropiada basándose en el input
     */
    fun determineTranslationStrategy(texto: String): TranslationStrategy {
        val textType = texto.getTextType()
        val isQuestion = texto.isQuestion()
        val isGreeting = texto.isGreeting()

        return when {
            // Saludos y preguntas comunes -> buscar en corpus primero
            isGreeting || isQuestion -> TranslationStrategy.EXACT_CORPUS_MATCH

            // Palabras individuales -> búsqueda directa en diccionario
            textType == TextUtils.TextType.SINGLE_WORD -> TranslationStrategy.WORD_BY_WORD_FALLBACK

            // Frases cortas -> búsqueda por similitud
            textType == TextUtils.TextType.SHORT_PHRASE -> TranslationStrategy.SIMILARITY_SEARCH

            // Oraciones completas -> corpus primero, luego similitud
            textType == TextUtils.TextType.SINGLE_SENTENCE -> TranslationStrategy.EXACT_CORPUS_MATCH

            // Textos largos -> estrategia mixta
            else -> TranslationStrategy.SIMILARITY_SEARCH
        }
    }

    /**
     * Calcula la confianza de una traducción basándose en varios factores
     */
    fun calculateTranslationConfidence(
        method: TranslationMethod,
        similarity: Float? = null,
        wordsFoundInCorpus: Int = 0,
        totalWords: Int = 1,
        isExpertValidated: Boolean = false
    ): Float {
        val baseConfidence = when (method) {
            TranslationMethod.EXACT_MATCH -> 0.95f
            TranslationMethod.SIMILAR_SENTENCE -> similarity ?: 0.8f
            TranslationMethod.WORD_BY_WORD -> {
                if (totalWords == 0) 0.5f
                else (wordsFoundInCorpus.toFloat() / totalWords) * 0.8f
            }
            TranslationMethod.USER_CORRECTION -> 0.7f
            TranslationMethod.HYBRID_AI -> 0.75f
            TranslationMethod.CONTEXTUAL_SEARCH -> 0.65f
        }

        // Aumentar confianza si está validado por experto
        val expertBonus = if (isExpertValidated) 0.1f else 0.0f

        return (baseConfidence + expertBonus).coerceIn(0.0f, 1.0f)
    }

    /**
     * Combina múltiples traducciones palabra por palabra en una oración natural
     */
    fun constructNaturalSentence(
        wordBreakdowns: List<WordBreakdown>,
        targetDirection: TranslationDirection
    ): String {
        if (wordBreakdowns.isEmpty()) return ""

        val words = wordBreakdowns.map { it.palabraTraducida }

        // Para Pamiwa (estructura SOV - Sujeto-Objeto-Verbo)
        return if (targetDirection == TranslationDirection.ES_TO_PAMIWA) {
            reorderForPamiwaSyntax(words)
        } else {
            // Para español, mantener orden más natural
            reorderForSpanishSyntax(words)
        }
    }

    /**
     * Reordena palabras para seguir la estructura SOV del Pamiwa
     */
    private fun reorderForPamiwaSyntax(words: List<String>): String {
        // Implementación básica - puedes mejorar con más conocimiento del Pamiwa
        // Por ahora, simplemente une las palabras
        return words.joinToString(" ")
    }

    /**
     * Reordena palabras para estructura natural del español
     */
    private fun reorderForSpanishSyntax(words: List<String>): String {
        return words.joinToString(" ")
    }

    /**
     * Crea un TranslationResponse a partir de diferentes fuentes
     */
    fun createTranslationResponse(
        originalText: String,
        translatedText: String,
        direction: TranslationDirection,
        method: TranslationMethod,
        confidence: Float = 0.5f,
        wordBreakdown: List<WordBreakdown> = emptyList(),
        similarSentences: List<SentenceModel> = emptyList(),
        fromCache: Boolean = false
    ): TranslationResponse {
        return TranslationResponse(
            textoOriginal = originalText,
            traduccionNatural = translatedText,
            direccion = direction,
            metodo = method,
            confianza = confidence,
            tiempoRespuesta = 0, // Se calculará en el repository
            desglosePalabras = wordBreakdown,
            oracionesSimilares = similarSentences,
            esDesdCache = fromCache
        )
    }

    /**
     * Valida si una corrección de usuario es razonable
     */
    fun validateUserCorrection(
        originalText: String,
        aiTranslation: String,
        userCorrection: String
    ): CorrectionValidationResult {
        val originalLength = originalText.length
        val correctionLength = userCorrection.trim().length

        // Verificaciones básicas
        when {
            userCorrection.trim().isEmpty() ->
                return CorrectionValidationResult.INVALID_EMPTY

            userCorrection.trim() == aiTranslation.trim() ->
                return CorrectionValidationResult.INVALID_SAME_AS_AI

            correctionLength > originalLength * 3 ->
                return CorrectionValidationResult.SUSPICIOUS_TOO_LONG

            correctionLength < originalLength / 3 ->
                return CorrectionValidationResult.SUSPICIOUS_TOO_SHORT

            userCorrection.all { it.isDigit() || it.isWhitespace() } ->
                return CorrectionValidationResult.SUSPICIOUS_ONLY_NUMBERS

            else -> return CorrectionValidationResult.VALID
        }
    }

    enum class CorrectionValidationResult {
        VALID,
        INVALID_EMPTY,
        INVALID_SAME_AS_AI,
        SUSPICIOUS_TOO_LONG,
        SUSPICIOUS_TOO_SHORT,
        SUSPICIOUS_ONLY_NUMBERS
    }

    /**
     * Genera sugerencias de mejora para una traducción
     */
    fun generateImprovementSuggestions(
        response: TranslationResponse
    ): List<String> {
        val suggestions = mutableListOf<String>()

        when {
            response.confianza < 0.5f -> {
                suggestions.add("Considera revisar la traducción, la confianza es baja")
            }

            response.metodo == TranslationMethod.WORD_BY_WORD && response.desglosePalabras.any { !it.encontradaEnCorpus } -> {
                suggestions.add("Algunas palabras no se encontraron en el corpus")
            }

            response.oracionesSimilares.isNotEmpty() -> {
                suggestions.add("Revisa las oraciones similares para mayor contexto")
            }
        }

        return suggestions
    }

    /**
     * Crea una clave de cache única para una traducción
     */
    fun createCacheKey(texto: String, direccion: TranslationDirection): String {
        val normalizedText = texto.normalizeForSearch()
        return "${normalizedText}_${direccion.name}".hashCode().toString()
    }

    /**
     * Determina si una traducción necesita validación del experto
     */
    fun needsExpertValidation(response: TranslationResponse): Boolean {
        return when {
            response.metodo == TranslationMethod.USER_CORRECTION && !response.esCorreccionUsuario -> true
            response.confianza < 0.6f -> true
            response.metodo == TranslationMethod.HYBRID_AI -> true
            else -> false
        }
    }
}