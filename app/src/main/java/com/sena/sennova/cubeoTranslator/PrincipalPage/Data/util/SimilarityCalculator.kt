package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util

import android.content.ContentValues.TAG
import android.util.Log
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.TextUtils.cleanForComparison
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.TextUtils.extractMeaningfulWords
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.max


//Calculadora de similitud entre textos
// Usa múltiples algoritmos para determinar qué tan similares son dos oraciones


class SimilarityCalculator @Inject constructor() {
    companion object {
        private const val TAG = "SimilarityCalculator"
        private const val MIN_WORD_LENGTH = 2 // Ignorar palabras muy cortas
    }

    /**
     Calcula similitud entre dos textos usando múltiples métricas
     Retorna un valor entre 0.0 (completamente diferentes) y 1.0 (idénticos)
     */
    fun calculateSimilarity(text1: String, text2: String): Float {
        if (text1.isEmpty() && text2.isEmpty()) return 1.0f
        if (text1.isEmpty() || text2.isEmpty()) return 0.0f

        val cleanText1 = text1.cleanForComparison()
        val cleanText2 = text2.cleanForComparison()

        // Si son exactamente iguales después de limpiar
        if (cleanText1 == cleanText2) return 1.0f

        // Calcular diferentes métricas de similitud
        val levenshteinSimilarity = calculateLevenshteinSimilarity(cleanText1, cleanText2)
        val wordSimilarity = calculateWordSimilarity(cleanText1, cleanText2)
        val lengthSimilarity = calculateLengthSimilarity(cleanText1, cleanText2)
        val structureSimilarity = calculateStructureSimilarity(cleanText1, cleanText2)

        // Combinar métricas con pesos diferentes
        val weightedSimilarity = (
                levenshteinSimilarity * 0.4f +  // 40% - Similitud de caracteres
                        wordSimilarity * 0.35f +        // 35% - Palabras en común
                        lengthSimilarity * 0.15f +      // 15% - Similitud de longitud
                        structureSimilarity * 0.1f      // 10% - Estructura gramatical
                )

        Log.d(TAG, "Similitud calculada entre '$text1' y '$text2': $weightedSimilarity")
        Log.d(TAG, "Desglose - Levenshtein: $levenshteinSimilarity, Palabras: $wordSimilarity, Longitud: $lengthSimilarity, Estructura: $structureSimilarity")

        return weightedSimilarity.coerceIn(0.0f, 1.0f)
    }

    /**
     * Similitud basada en distancia de Levenshtein normalizada
     */
    private fun calculateLevenshteinSimilarity(text1: String, text2: String): Float {
        val distance = levenshteinDistance(text1, text2)
        val maxLength = max(text1.length, text2.length)

        return if (maxLength == 0) 1.0f else 1.0f - (distance.toFloat() / maxLength)
    }

    /**
     * Similitud basada en palabras en común
     */
    private fun calculateWordSimilarity(text1: String, text2: String): Float {
        val words1 = text1.extractMeaningfulWords()
        val words2 = text2.extractMeaningfulWords()

        if (words1.isEmpty() && words2.isEmpty()) return 1.0f
        if (words1.isEmpty() || words2.isEmpty()) return 0.0f

        val commonWords = words1.intersect(words2.toSet()).size
        val totalUniqueWords = (words1 + words2).toSet().size

        return if (totalUniqueWords == 0) 0.0f else (commonWords * 2.0f) / totalUniqueWords
    }

    /**
     * Similitud basada en longitud de los textos
     */
    private fun calculateLengthSimilarity(text1: String, text2: String): Float {
        val len1 = text1.length
        val len2 = text2.length
        val maxLen = max(len1, len2)

        return if (maxLen == 0) 1.0f else 1.0f - (kotlin.math.abs(len1 - len2).toFloat() / maxLen)
    }

    /**
     * Similitud basada en estructura gramatical (número de palabras, puntuación, etc.)
     */
    private fun calculateStructureSimilarity(text1: String, text2: String): Float {
        val words1Count = text1.split("\\s+".toRegex()).size
        val words2Count = text2.split("\\s+".toRegex()).size
        val punctuation1 = text1.count { ".,!?¡¿;:".contains(it) }
        val punctuation2 = text2.count { ".,!?¡¿;:".contains(it) }

        val wordCountSimilarity = if (max(words1Count, words2Count) == 0) 1.0f
        else 1.0f - (kotlin.math.abs(words1Count - words2Count).toFloat() / max(words1Count, words2Count))

        val punctuationSimilarity = if (max(punctuation1, punctuation2) == 0) 1.0f
        else 1.0f - (kotlin.math.abs(punctuation1 - punctuation2).toFloat() / max(punctuation1, punctuation2))

        return (wordCountSimilarity + punctuationSimilarity) / 2.0f
    }

    /**
     * Algoritmo de distancia de Levenshtein optimizado
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        // Optimización: si las strings son muy diferentes en longitud, retornar rápido
        if (kotlin.math.abs(len1 - len2) > max(len1, len2) / 2) {
            return max(len1, len2)
        }

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        // Inicializar primera fila y columna
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        // Llenar la matriz
        for (i in 1..len1) {
            for (j in 1..len2) {
                dp[i][j] = if (s1[i-1] == s2[j-1]) {
                    dp[i-1][j-1] // No hay costo si los caracteres son iguales
                } else {
                    1 + minOf(
                        dp[i-1][j],     // Eliminación
                        dp[i][j-1],     // Inserción
                        dp[i-1][j-1]    // Sustitución
                    )
                }
            }
        }

        return dp[len1][len2]
    }

    /**
     * Encuentra las mejores coincidencias de una lista basándose en similitud
     */
    fun findBestMatches(
        query: String,
        candidates: List<String>,
        threshold: Float = 0.5f,
        maxResults: Int = 5
    ): List<Pair<String, Float>> {
        return candidates
            .map { candidate -> candidate to calculateSimilarity(query, candidate) }
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .take(maxResults)
    }

    /**
     * Determina si dos textos son "suficientemente similares" para considerarse una coincidencia
     */
    fun areSimilar(text1: String, text2: String, threshold: Float = 0.7f): Boolean {
        return calculateSimilarity(text1, text2) >= threshold
    }
}

