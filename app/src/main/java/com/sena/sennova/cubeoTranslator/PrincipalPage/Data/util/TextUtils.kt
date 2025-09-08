package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util

import java.text.Normalizer

/**
 * Utilidades para procesamiento de texto
 */
object TextUtils {

    // =============================================================================
    // ENUMS Y TIPOS (AGREGAR AL INICIO)
    // =============================================================================

    /**
     * Tipos de texto para análisis
     */
    enum class TextType {
        SINGLE_WORD,
        SHORT_PHRASE,
        SINGLE_SENTENCE,
        MULTIPLE_SENTENCES
    }

    /**
     * Resultado de análisis de texto
     */
    data class TextAnalysis(
        val originalText: String,
        val detectedLanguage: String,
        val meaningfulWords: List<String>,
        val keywords: List<String>,
        val isQuestion: Boolean,
        val isGreeting: Boolean,
        val textType: TextType,
        val confidence: Float
    )

    // =============================================================================
    // STOP WORDS Y CONFIGURACIÓN
    // =============================================================================

    // Palabras comunes en español que no aportan mucho significado para similitud
    private val SPANISH_STOP_WORDS = setOf(
        "el", "la", "de", "que", "y", "a", "en", "un", "es", "se", "no", "te", "lo", "le", "da", "su", "por", "son",
        "con", "para", "al", "del", "los", "las", "una", "uno", "este", "esta", "estos", "estas", "ese", "esa", "esos", "esas",
        "aquel", "aquella", "aquellos", "aquellas", "mi", "tu", "su", "nuestro", "nuestra", "vuestro", "vuestra",
        "me", "te", "se", "nos", "os", "le", "les", "lo", "la", "los", "las",
        "muy", "más", "menos", "tanto", "tan", "mucho", "poco", "bastante", "demasiado",
        "si", "pero", "aunque", "porque", "cuando", "donde", "como", "quien", "cual", "cuanto"
    )

    // Palabras estructurales en Pamiwa que aparecen frecuentemente pero no definen el significado principal
    private val PAMIWA_STRUCTURAL_WORDS = setOf(
        // Partículas temporales y aspectuales
        "wi", "wa", "wɨ", "ka", "ga", "ja", "ra", "ta", "da", "na", "ma", "ba", "pa", "sa", "cha", "kha",

        // Partículas de evidencialidad y modalidad
        "tɨ", "dɨ", "rɨ", "sɨ", "kɨ", "pɨ", "mɨ", "bɨ", "jɨ", "nɨ",

        // Marcadores de género y clasificadores muy generales
        "ko", "go", "jo", "ro", "so", "cho", "kho",

        // Conectores y partículas discursivas básicas
        "ã", "ẽ", "ĩ", "õ", "ũ", "ỹ",

        // Deícticos básicos muy frecuentes
        "ti", "di", "ri", "si", "chi", "khi"
    )

    /**
     * Configuración específica para análisis de texto Pamiwa
     */
    object PamiwaConfig {
        const val MIN_WORD_LENGTH_PAMIWA = 1
        const val MIN_WORD_LENGTH_SPANISH = 2
        const val LANGUAGE_DETECTION_THRESHOLD = 0.6f
        const val FILTER_STRUCTURAL_ONLY_IN_LONG_SENTENCES = true
        const val LONG_SENTENCE_WORD_COUNT = 4
    }

    // =============================================================================
    // FUNCIONES PRINCIPALES
    // =============================================================================

    /**
     * Limpia texto para comparación, removiendo acentos, puntuación y normalizando espacios
     */
    fun String.cleanForComparison(): String {
        return this
            .lowercase()
            .removeAccents()
            .replace(Regex("[^a-z\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Remueve acentos de un texto
     */
    fun String.removeAccents(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("[^\\p{ASCII}]"), "")
    }

    /**
     * Extrae palabras significativas (sin stop words)
     * Adaptado para las características específicas del Pamiwa
     */
    fun String.extractMeaningfulWords(): List<String> {
        val words = this.cleanForComparison()
            .split("\\s+".toRegex())
            .filter { it.length >= 1 }

        return if (this.isLikelySpanish()) {
            words.filter { word ->
                word !in SPANISH_STOP_WORDS &&
                        word.isNotBlank() &&
                        word.length >= 2
            }
        } else {
            filterPamiwaWords(words)
        }
    }

    /**
     * Filtro específico para palabras en Pamiwa
     */
    private fun filterPamiwaWords(words: List<String>): List<String> {
        return words.filter { word ->
            when {
                word.isBlank() -> false
                word.length < 1 -> false
                words.size > 3 && word in PAMIWA_STRUCTURAL_WORDS -> false
                word.all { it.isDigit() } -> false
                else -> true
            }
        }
    }

    /**
     * Detecta si un texto es probablemente español
     */
    fun String.isLikelySpanish(): Boolean {
        val lowerText = this.lowercase()

        val strongSpanishIndicators = listOf(
            "el ", "la ", "los ", "las ", "un ", "una ", "de ", "del ", "al ",
            "que ", "con ", "para ", "por ", "como ", "donde ", "cuando ",
            "ción", "dad", "mente", "ando", "iendo"
        )

        val pamiwaIndicators = listOf(
            "kɨ", "tɨ", "dɨ", "rɨ", "sɨ", "pɨ", "mɨ", "bɨ", "jɨ", "nɨ",
            "ɨa", "ɨe", "ɨi", "ɨo", "ɨu",
            "kh", "th", "ph", "ch",
            "wa", "wi", "we", "wo", "wu"
        )

        val spanishMatches = strongSpanishIndicators.count { lowerText.contains(it) }
        val pamiwaMatches = pamiwaIndicators.count { lowerText.contains(it) }

        return spanishMatches > pamiwaMatches
    }

    /**
     * Detecta características específicas del Pamiwa
     */
    fun String.isProbablyPamiwa(): Boolean {
        val lowerText = this.lowercase()

        val pamiwaFeatures = listOf(
            "ɨ",
            "kh", "th", "ph",
            "wa", "wi", "we", "wo", "wu",
            "ya", "ye", "yi", "yo", "yu",
            "ã", "ẽ", "ĩ", "õ", "ũ"
        )

        return pamiwaFeatures.any { lowerText.contains(it) }
    }

    /**
     * Detecta si un texto contiene múltiples palabras
     */
    fun String.isMultipleWords(): Boolean {
        return this.trim().contains(Regex("\\s+"))
    }

    /**
     * Normaliza texto para búsqueda en base de datos
     */
    fun String.normalizeForSearch(): String {
        return this
            .trim()
            .lowercase()
            .removeAccents()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Extrae palabras clave específicas según el idioma detectado
     */
    fun String.extractKeywords(maxKeywords: Int = 3): List<String> {
        val meaningfulWords = this.extractMeaningfulWords()

        return if (this.isLikelySpanish()) {
            meaningfulWords
                .sortedByDescending { it.length }
                .take(maxKeywords)
        } else {
            meaningfulWords
                .sortedByDescending { word ->
                    var score = word.length.toFloat()

                    if (word.contains("ɨ")) score += 2
                    if (word.contains(Regex("[ktp]h"))) score += 1.5f
                    if (word.contains(Regex("[ãẽĩõũỹ]"))) score += 1.5f
                    if (word.length >= 4) score += 1

                    score
                }
                .take(maxKeywords)
        }
    }

    /**
     * Crea variaciones de un texto para búsqueda más flexible
     */
    fun String.createSearchVariations(): List<String> {
        val original = this.trim()
        val variations = mutableListOf<String>()

        variations.add(original)
        variations.add(original.lowercase())
        variations.add(original.cleanForComparison())
        variations.add(original.normalizeForSearch())
        variations.add(original.replace(Regex("[.,!?¡¿;:]"), "").trim())

        return variations.distinct()
    }

    /**
     * Calcula el número de palabras significativas en común entre dos textos
     */
    fun countCommonMeaningfulWords(text1: String, text2: String): Int {
        val words1 = text1.extractMeaningfulWords().toSet()
        val words2 = text2.extractMeaningfulWords().toSet()

        return words1.intersect(words2).size
    }

    /**
     * Determina el tipo de texto (palabra, frase corta, oración, párrafo)
     */
    fun String.getTextType(): TextType {
        val cleanText = this.trim()
        val wordCount = cleanText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        val sentenceCount = cleanText.split("[.!?]+".toRegex()).filter { it.trim().isNotBlank() }.size

        return when {
            wordCount == 1 -> TextType.SINGLE_WORD
            wordCount <= 3 -> TextType.SHORT_PHRASE
            sentenceCount == 1 -> TextType.SINGLE_SENTENCE
            else -> TextType.MULTIPLE_SENTENCES
        }
    }

    /**
     * Crea un hash único para un texto normalizado (útil para cache)
     */
    fun String.createNormalizedHash(): String {
        return this.normalizeForSearch().hashCode().toString()
    }

    /**
     * Verifica si un texto contiene patrones de pregunta en cualquier idioma
     */
    fun String.isQuestion(): Boolean {
        val lowerText = this.lowercase().removeAccents()

        val spanishQuestionWords = listOf(
            "que", "como", "cuando", "donde", "por que", "quien", "cual", "cuanto"
        )

        val pamiwaQuestionWords = listOf(
            "jawe", "jáwé",
            "waga", "wagá",
            "kari", "karí"
        )

        val hasQuestionMark = this.contains("?")
        val hasQuestionWords = (spanishQuestionWords + pamiwaQuestionWords)
            .any { lowerText.contains(it) }

        return hasQuestionMark || hasQuestionWords
    }

    /**
     * Verifica si un texto contiene patrones de saludo en cualquier idioma
     */
    fun String.isGreeting(): Boolean {
        val lowerText = this.lowercase().removeAccents()

        val spanishGreetings = listOf("hola", "buenos", "buenas", "saludos", "hi", "hello")
        val pamiwaGreetings = listOf(
            "táchi", "tachi",
            "kóba", "koba"
        )

        val allGreetings = spanishGreetings + pamiwaGreetings
        return allGreetings.any { lowerText.contains(it) }
    }

    /**
     * Trunca texto manteniendo palabras completas
     */
    fun String.truncateWords(maxLength: Int): String {
        if (this.length <= maxLength) return this

        val truncated = this.take(maxLength)
        val lastSpace = truncated.lastIndexOf(' ')

        return if (lastSpace > maxLength / 2) {
            truncated.take(lastSpace) + "..."
        } else {
            truncated + "..."
        }
    }

    /**
     * Análisis adaptativo según el idioma detectado
     */
    fun String.analyzeAdaptively(): TextAnalysis {
        val isSpanish = this.isLikelySpanish()
        val isPamiwa = this.isProbablyPamiwa()

        return TextAnalysis(
            originalText = this,
            detectedLanguage = when {
                isSpanish -> "español"
                isPamiwa -> "pamiwa"
                else -> "desconocido"
            },
            meaningfulWords = this.extractMeaningfulWords(),
            keywords = this.extractKeywords(),
            isQuestion = this.isQuestion(),
            isGreeting = this.isGreeting(),
            textType = this.getTextType(),
            confidence = if (isSpanish || isPamiwa) 0.8f else 0.4f
        )
    }
}