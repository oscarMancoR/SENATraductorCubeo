package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.implementar

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore


import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.LocalDataSource
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.toSentenceModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.toWordModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val localDataSource: LocalDataSource,
    private val syncManager: SyncManager,
    private val similarityCalculator: SimilarityCalculator
) : TranslationRepository {

    companion object {
        private const val TAG = "TranslationRepository"
        private const val COLLECTION_USER_CORRECTIONS = "correcciones_usuarios"
        private const val SIMILARITY_THRESHOLD = 0.7f
    }

    // Estados
    private val _corpusLoadingState = MutableStateFlow(CorpusLoadingState.LOADING)

    // Cache en memoria (ligero)
    private val extractedWordsCache = mutableMapOf<String, String>()

    init {
        // Inicializar sincronización al crear el repository
        initializeCorpus()
    }

    private fun initializeCorpus() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                // Verificar si hay datos locales
                val hasData = syncManager.hasLocalData()

                if (!hasData) {
                    Log.d(TAG, "No hay datos locales, sincronizando desde Firebase...")
                    _corpusLoadingState.value = CorpusLoadingState.LOADING
                    syncManager.performFullSync()
                } else {
                    // Sincronizar en background si es necesario
                    _corpusLoadingState.value = CorpusLoadingState.LOADED
                    syncManager.syncIfNeeded()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error inicializando corpus", e)
                _corpusLoadingState.value = CorpusLoadingState.ERROR
            }
        }

        // Observar estado de sync
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            syncManager.syncState.collect { syncState ->
                _corpusLoadingState.value = when (syncState) {
                    is com.sena.sennova.cubeoTranslator.PrincipalPage.Data.SyncState.InProgress->
                        CorpusLoadingState.LOADING
                    is com.sena.sennova.cubeoTranslator.PrincipalPage.Data.SyncState.Success ->
                        CorpusLoadingState.LOADED
                    is com.sena.sennova.cubeoTranslator.PrincipalPage.Data.SyncState.Error ->
                        CorpusLoadingState.ERROR
                    else -> _corpusLoadingState.value
                }
            }
        }
    }

    // =============================================================================
    // TRADUCCIÓN PRINCIPAL - AHORA USA ROOM
    // =============================================================================

    override suspend fun translateText(
        texto: String,
        direccion: TranslationDirection,
        modo: TranslationMode
    ): Result<TranslationResponse> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            val textoLimpio = texto.trim()
            if (textoLimpio.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Texto vacío"))
            }

            Log.d(TAG, "Traduciendo: '$textoLimpio' (${direccion.name})")

            // 1. Verificar correcciones del usuario (Firebase)
            getUserCorrection(textoLimpio, direccion)?.let { correction ->
                return@withContext Result.success(
                    createResponseFromCorrection(correction, textoLimpio, startTime)
                )
            }

            // 2. Buscar coincidencia exacta en Room
            val exactMatch = findExactMatch(textoLimpio, direccion)
            if (exactMatch != null) {
                return@withContext Result.success(
                    createResponseFromExactMatch(exactMatch, textoLimpio, direccion, startTime)
                )
            }

            // 3. Buscar oraciones similares en Room
            val similarSentences = findSimilarSentences(textoLimpio, direccion, limit = 3)
            if (similarSentences.isNotEmpty()) {
                val bestMatch = similarSentences.first()
                val similarity = calculateSimilarity(textoLimpio, bestMatch, direccion)

                if (similarity >= SIMILARITY_THRESHOLD) {
                    return@withContext Result.success(
                        createResponseFromSimilar(
                            bestMatch, textoLimpio, direccion, similarity, startTime, similarSentences
                        )
                    )
                }
            }

            // 4. Traducción palabra por palabra
            val wordByWordResult = translateWordByWordOnly(textoLimpio, direccion, modo, startTime)
            Result.success(wordByWordResult)

        } catch (e: Exception) {
            Log.e(TAG, "Error en translateText", e)
            Result.failure(e)
        }
    }

    // =============================================================================
    // BÚSQUEDA EN ROOM (RÁPIDO - OFFLINE)
    // =============================================================================

    override suspend fun findExactMatch(
        texto: String,
        direccion: TranslationDirection
    ): SentenceModel? = withContext(Dispatchers.IO) {
        try {
            val oracionEntity = if (direccion == TranslationDirection.ES_TO_PAMIWA) {
                localDataSource.findOracionExactaEspanol(texto.lowercase().trim())
            } else {
                localDataSource.findOracionExactaPamiwa(texto.lowercase().trim())
            }

            oracionEntity?.toSentenceModel()
        } catch (e: Exception) {
            Log.e(TAG, "Error en findExactMatch", e)
            null
        }
    }

    override suspend fun findSimilarSentences(
        texto: String,
        direccion: TranslationDirection,
        limit: Int
    ): List<SentenceModel> = withContext(Dispatchers.IO) {
        try {
            val palabras = texto.lowercase().split(" ").filter { it.length > 2 }
            val resultados = mutableSetOf<SentenceModel>()

            // Buscar por cada palabra clave en Room
            for (palabra in palabras.take(3)) { // Limitar a 3 keywords
                val oraciones = localDataSource.searchOracionesByKeyword(palabra)
                resultados.addAll(oraciones.map { it.toSentenceModel() })
            }

            // Calcular similitud y ordenar
            resultados.map { sentence ->
                sentence to calculateSimilarity(texto, sentence, direccion)
            }
                .filter { it.second >= 0.3f }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }

        } catch (e: Exception) {
            Log.e(TAG, "Error en findSimilarSentences", e)
            emptyList()
        }
    }

    // =============================================================================
    // TRADUCCIÓN PALABRA POR PALABRA - USA ROOM + EXTRACCIÓN
    // =============================================================================

    override suspend fun translateWordByWord(
        texto: String,
        direccion: TranslationDirection
    ): List<WordBreakdown> = withContext(Dispatchers.IO) {
        try {
            val palabras = texto.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            val resultados = mutableListOf<WordBreakdown>()

            palabras.forEach { palabra ->
                val palabraLimpia = palabra.lowercase()
                    .replace(Regex("[^a-záéíóúñü]"), "")

                // PASO 1: Buscar en Room (corpus de palabras)
                val palabraEntity = if (direccion == TranslationDirection.ES_TO_PAMIWA) {
                    localDataSource.findPalabraByEspanol(palabraLimpia)
                } else {
                    localDataSource.findPalabraByPamiwa(palabraLimpia)
                }

                if (palabraEntity != null) {
                    // Encontrada en corpus local
                    resultados.add(WordBreakdown(
                        palabraOriginal = palabra,
                        palabraTraducida = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                            palabraEntity.palabra_pamiwa else palabraEntity.palabra_espanol,
                        categoria = palabraEntity.categoria ?: "general",
                        confianza = 0.9f,
                        encontradaEnCorpus = true
                    ))
                } else {
                    // PASO 2: Intentar extraer desde oraciones locales
                    val extractedWord = getCachedExtractedWord(palabraLimpia, direccion)
                        ?: extractWordFromLocalSentences(palabraLimpia, direccion)

                    if (extractedWord != null) {
                        resultados.add(WordBreakdown(
                            palabraOriginal = palabra,
                            palabraTraducida = extractedWord,
                            categoria = "extracted_from_sentence",
                            confianza = 0.7f,
                            encontradaEnCorpus = false
                        ))
                    } else {
                        // No encontrada
                        resultados.add(WordBreakdown(
                            palabraOriginal = palabra,
                            palabraTraducida = palabra,
                            categoria = "unknown",
                            confianza = 0.1f,
                            encontradaEnCorpus = false
                        ))
                    }
                }
            }

            resultados
        } catch (e: Exception) {
            Log.e(TAG, "Error en translateWordByWord", e)
            emptyList()
        }
    }

    // =============================================================================
    // EXTRACCIÓN DE PALABRAS DESDE ORACIONES LOCALES (OFFLINE)
    // =============================================================================

    private suspend fun extractWordFromLocalSentences(
        palabra: String,
        direccion: TranslationDirection
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Buscar oraciones que contengan esta palabra
            val oraciones = localDataSource.searchOracionesByKeyword(palabra)

            if (oraciones.isEmpty()) return@withContext null

            // Convertir a modelos de dominio
            val sentenceModels = oraciones.map { it.toSentenceModel() }

            // Extraer usando alineación
            val traduccionExtraida = extractWordUsingAlignment(
                palabra = palabra,
                oraciones = sentenceModels,
                direccion = direccion
            )

            if (traduccionExtraida != null) {
                cacheExtractedWord(palabra, traduccionExtraida, direccion)
            }

            traduccionExtraida

        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo palabra desde oraciones locales", e)
            null
        }
    }

    private fun extractWordUsingAlignment(
        palabra: String,
        oraciones: List<SentenceModel>,
        direccion: TranslationDirection
    ): String? {
        val palabrasCandidatas = mutableMapOf<String, Int>()

        for (oracion in oraciones) {
            val textoOrigen = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                oracion.oracionEspanol else oracion.oracionPamiwa
            val textoDestino = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                oracion.oracionPamiwa else oracion.oracionEspanol

            val palabrasOrigen = textoOrigen.lowercase()
                .replace(Regex("[^a-záéíóúñü\\s]"), "")
                .split("\\s+".toRegex())
                .filter { it.isNotEmpty() }

            val palabrasDestino = textoDestino.lowercase()
                .replace(Regex("[^a-záéíóúñüɨɵɛ\\s~]"), "")
                .split("\\s+".toRegex())
                .filter { it.isNotEmpty() }

            val posicion = palabrasOrigen.indexOfFirst {
                it.contains(palabra) || palabra.contains(it)
            }

            if (posicion != -1 && posicion < palabrasDestino.size) {
                val candidata = palabrasDestino[posicion]
                palabrasCandidatas[candidata] = palabrasCandidatas.getOrDefault(candidata, 0) + 1
            }
        }

        return palabrasCandidatas.maxByOrNull { it.value }?.key
    }

    // =============================================================================
    // CACHE DE PALABRAS EXTRAÍDAS
    // =============================================================================

    private fun cacheExtractedWord(palabra: String, traduccion: String, direccion: TranslationDirection) {
        val key = "${palabra}_${direccion.name}"
        extractedWordsCache[key] = traduccion
    }

    private fun getCachedExtractedWord(palabra: String, direccion: TranslationDirection): String? {
        val key = "${palabra}_${direccion.name}"
        return extractedWordsCache[key]
    }

    // =============================================================================
    // CORRECCIONES DE USUARIO (FIREBASE)
    // =============================================================================

    override suspend fun saveUserCorrection(correction: UserCorrectionModel): Result<Boolean> {
        return try {
            val correctionWithDefaults = correction.copy(
                id = "",
                timestamp = System.currentTimeMillis(),
                aplicadaInmediatamente = true,
                estadoValidacion = ValidationStatus.PENDING
            )

            firestore.collection(COLLECTION_USER_CORRECTIONS)
                .add(correctionWithDefaults)
                .await()

            Log.d(TAG, "Corrección guardada exitosamente")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando corrección", e)
            Result.failure(e)
        }
    }

    override suspend fun getUserCorrection(
        texto: String,
        direccion: TranslationDirection
    ): UserCorrectionModel? {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_USER_CORRECTIONS)
                .whereEqualTo("textoOriginal", texto.lowercase().trim())
                .whereEqualTo("direccion", direccion.name)
                .whereEqualTo("aplicadaInmediatamente", true)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            querySnapshot.documents.firstOrNull()?.let { doc ->
                doc.toObject(UserCorrectionModel::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo corrección del usuario", e)
            null
        }
    }

    // =============================================================================
    // GESTIÓN DEL CORPUS
    // =============================================================================

    override suspend fun loadCorpusFromFirebase(): Result<Boolean> {
        return syncManager.performFullSync().map { true }
    }

    override fun getCorpusLoadingState(): Flow<CorpusLoadingState> = _corpusLoadingState.asStateFlow()

    override suspend fun isCorpusReady(): Boolean {
        return _corpusLoadingState.value == CorpusLoadingState.LOADED
    }

    override suspend fun getCorpusStats(): CorpusStats {
        return try {
            val syncStats = syncManager.getSyncStats()
            CorpusStats(
                totalPalabras = syncStats.totalPalabras,
                totalOraciones = syncStats.totalOraciones,
                totalCorreccionesPendientes = 0, // Implementar si es necesario
                ultimaActualizacion = syncStats.lastSyncPalabras ?: 0L,
                precisionPromedio = 0.85f
            )
        } catch (e: Exception) {
            CorpusStats(0, 0, 0, 0, 0f)
        }
    }

    // =============================================================================
    // FUNCIONES AUXILIARES
    // =============================================================================

    private fun calculateSimilarity(
        texto: String,
        sentence: SentenceModel,
        direccion: TranslationDirection
    ): Float {
        val textoComparar = if (direccion == TranslationDirection.ES_TO_PAMIWA)
            sentence.oracionEspanol else sentence.oracionPamiwa

        return similarityCalculator.calculateSimilarity(
            texto.lowercase(),
            textoComparar.lowercase()
        )
    }

    private suspend fun translateWordByWordOnly(
        texto: String,
        direccion: TranslationDirection,
        modo: TranslationMode,
        startTime: Long
    ): TranslationResponse {
        val wordBreakdown = translateWordByWord(texto, direccion)
        val traduccionLiteral = wordBreakdown.joinToString(" ") { it.palabraTraducida }

        val traduccionNatural = if (modo == TranslationMode.NATURAL) {
            TranslationUtils.constructNaturalSentence(wordBreakdown, direccion)
        } else {
            traduccionLiteral
        }

        val confidence = TranslationUtils.calculateTranslationConfidence(
            method = TranslationMethod.PALABRA_POR_PALABRA,
            wordsFoundInCorpus = wordBreakdown.count { it.encontradaEnCorpus },
            totalWords = wordBreakdown.size
        )

        return TranslationResponse(
            textoOriginal = texto,
            traduccionNatural = traduccionNatural,
            traduccionLiteral = if (modo == TranslationMode.BOTH) traduccionLiteral else null,
            direccion = direccion,
            metodo = TranslationMethod.PALABRA_POR_PALABRA,
            confianza = confidence,
            tiempoRespuesta = System.currentTimeMillis() - startTime,
            desglosePalabras = wordBreakdown
        )
    }

    private fun createResponseFromExactMatch(
        sentence: SentenceModel,
        originalText: String,
        direction: TranslationDirection,
        startTime: Long
    ): TranslationResponse {
        val translation = if (direction == TranslationDirection.ES_TO_PAMIWA)
            sentence.oracionPamiwa else sentence.oracionEspanol

        return TranslationResponse(
            textoOriginal = originalText,
            traduccionNatural = translation,
            direccion = direction,
            metodo = TranslationMethod.COINCIDENCIA_EXACTA,
            confianza = sentence.confianza,
            tiempoRespuesta = System.currentTimeMillis() - startTime
        )
    }

    private fun createResponseFromSimilar(
        sentence: SentenceModel,
        originalText: String,
        direction: TranslationDirection,
        similarity: Float,
        startTime: Long,
        similarSentences: List<SentenceModel>
    ): TranslationResponse {
        val translation = if (direction == TranslationDirection.ES_TO_PAMIWA)
            sentence.oracionPamiwa else sentence.oracionEspanol

        return TranslationResponse(
            textoOriginal = originalText,
            traduccionNatural = translation,
            direccion = direction,
            metodo = TranslationMethod.ORACION_SIMILAR,
            confianza = similarity * sentence.confianza,
            tiempoRespuesta = System.currentTimeMillis() - startTime,
            oracionesSimilares = similarSentences
        )
    }

    private fun createResponseFromCorrection(
        correction: UserCorrectionModel,
        originalText: String,
        startTime: Long
    ): TranslationResponse {
        return TranslationResponse(
            textoOriginal = originalText,
            traduccionNatural = correction.correccionUsuario,
            direccion = correction.direccion,
            metodo = TranslationMethod.USER_CORRECTION,
            confianza = correction.confianzaUsuario,
            tiempoRespuesta = System.currentTimeMillis() - startTime,
            esCorreccionUsuario = true,
            requiereValidacionExperto = !correction.validadaPorExperto
        )
    }

    // =============================================================================
    // MÉTODOS NO IMPLEMENTADOS O DELEGADOS
    // =============================================================================

    override suspend fun translateWord(palabra: String, direccion: TranslationDirection): Result<WordBreakdown> {
        val breakdown = translateWordByWord(palabra, direccion)
        return if (breakdown.isNotEmpty()) Result.success(breakdown.first())
        else Result.failure(Exception("Palabra no encontrada"))
    }

    override suspend fun translateSentence(oracion: String, direccion: TranslationDirection): Result<TranslationResponse> {
        return translateText(oracion, direccion, TranslationMode.NATURAL)
    }

    override suspend fun findWordInCorpus(palabra: String, direccion: TranslationDirection): WordModel? {
        val entity = if (direccion == TranslationDirection.ES_TO_PAMIWA) {
            localDataSource.findPalabraByEspanol(palabra)
        } else {
            localDataSource.findPalabraByPamiwa(palabra)
        }
        return entity?.toWordModel()
    }

    override suspend fun getPendingCorrections(): List<UserCorrectionModel> = emptyList()
    override suspend fun markCorrectionAsValidated(correctionId: String, status: ValidationStatus, expertComment: String): Result<Boolean> = Result.success(false)
    override suspend fun applyValidatedCorrections(): Result<Int> = Result.success(0)
    override suspend fun getCachedTranslation(texto: String, direccion: TranslationDirection): TranslationCacheModel? = null
    override suspend fun cacheTranslation(cache: TranslationCacheModel): Result<Boolean> = Result.success(false)
    override suspend fun clearExpiredCache(): Result<Int> = Result.success(0)
    override suspend fun getCacheSize(): Int = 0
    override suspend fun buscarTraduccionesCompatible(oracion: String): List<String> = emptyList()
    override suspend fun buscarEnColeccionActual(palabra: String): String? = null
    override suspend fun getUsageMetrics(): TranslationMetrics = TranslationMetrics(0,0,0,0,0,0f,0L, emptyList(), emptyList())
    override suspend fun reportTranslationError(originalText: String, translation: String, errorType: String): Result<Boolean> = Result.success(false)
}

/*
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.TranslationRepository
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.TranslationStrategy
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.SimilarityCalculator
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.TranslationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val similarityCalculator: SimilarityCalculator
) : TranslationRepository {

    companion object {
        private const val TAG = "TranslationRepository"

        // Colecciones Firebase
        private const val COLLECTION_EXPERT_WORDS = "expert_words"
        private const val COLLECTION_EXPERT_SENTENCES = "expert_sentences"
        private const val COLLECTION_LEGACY = "tu_coleccion" // Tu colección actual

        private const val COLLECTION_WORDS = "tu_coleccion"  // Tu colección de palabras actual
        private const val COLLECTION_SENTENCES = "oraciones_coleccion"  // Tu colección de oraciones
        private const val COLLECTION_USER_CORRECTIONS = "correcciones_usuarios"  // Nueva colección

        // Umbrales
        private const val SIMILARITY_THRESHOLD = 0.7f
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L
    }

    // Estados del repository
    private val _corpusLoadingState = MutableStateFlow(CorpusLoadingState.LOADING)
    private val _corpusStats = MutableStateFlow(CorpusStats(0, 0, 0, 0, 0f))

    // Cache en memoria (simple)
    private val memoryCache = mutableMapOf<String, TranslationCacheModel>()

    // =============================================================================
    // TRADUCCIÓN PRINCIPAL
    // =============================================================================

    override suspend fun translateText(
        texto: String,
        direccion: TranslationDirection,
        modo: TranslationMode
    ): Result<TranslationResponse> = withContext(Dispatchers.IO) {

        val startTime = System.currentTimeMillis()

        try {
            val textoLimpio = texto.trim()
            if (textoLimpio.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Texto vacío"))
            }

            Log.d(TAG, "Traduciendo: '$textoLimpio' (${direccion.name}, ${modo.name})")

            // 1. Verificar cache primero
            val cacheKey = TranslationUtils.createCacheKey(textoLimpio, direccion)
            getCachedTranslation(textoLimpio, direccion)?.let { cached ->
                if (!isCacheExpired(cached)) {
                    Log.d(TAG, "Traducción encontrada en cache")
                    return@withContext Result.success(cached.toTranslationResponse(startTime))
                }
            }

            // 2. Verificar correcciones del usuario primero (máxima prioridad)
            getUserCorrection(textoLimpio, direccion)?.let { correction ->
                Log.d(TAG, "Usando corrección previa del usuario")
                val response = createResponseFromCorrection(correction, textoLimpio, startTime)
                cacheResult(response, cacheKey)
                return@withContext Result.success(response)
            }

            // 3. Determinar estrategia de traducción
            val strategy = TranslationUtils.determineTranslationStrategy(textoLimpio)
            Log.d(TAG, "Estrategia determinada: $strategy")

            // 4. Ejecutar traducción según estrategia y modo
            val response = when (strategy) {
                TranslationStrategy.EXACT_CORPUS_MATCH -> {
                    findExactMatch(textoLimpio, direccion)?.let { sentence ->
                        createResponseFromExactMatch(sentence, textoLimpio, direccion, startTime)
                    } ?: translateWithSimilarity(textoLimpio, direccion, modo, startTime)
                }

                TranslationStrategy.SIMILARITY_SEARCH -> {
                    translateWithSimilarity(textoLimpio, direccion, modo, startTime)
                }

                TranslationStrategy.WORD_BY_WORD_FALLBACK -> {
                    translateWordByWordOnly(textoLimpio, direccion, modo, startTime)
                }

                else -> translateWithSimilarity(textoLimpio, direccion, modo, startTime)
            }

            // 5. Cache el resultado
            cacheResult(response, cacheKey)

            Log.d(TAG, "Traducción completada: ${response.metodo} - Confianza: ${response.confianza}")
            Result.success(response)

        } catch (e: Exception) {
            Log.e(TAG, "Error en translateText", e)
            Result.failure(e)
        }
    }

    // =============================================================================
    // ESTRATEGIAS DE TRADUCCIÓN
    // =============================================================================

    private suspend fun translateWithSimilarity(
        texto: String,
        direccion: TranslationDirection,
        modo: TranslationMode,
        startTime: Long
    ): TranslationResponse {

        // 1. Buscar oraciones similares
        val similarSentences = findSimilarSentences(texto, direccion, limit = 3)

        if (similarSentences.isNotEmpty()) {
            val bestMatch = similarSentences.first()
            val similarity = similarityCalculator.calculateSimilarity(
                texto.lowercase(),
                if (direccion == TranslationDirection.ES_TO_PAMIWA)
                    bestMatch.oracionEspanol.lowercase()
                else
                    bestMatch.oracionPamiwa.lowercase()
            )

            if (similarity >= SIMILARITY_THRESHOLD) {
                Log.d(TAG, "Oración similar encontrada con similitud: $similarity")
                return createResponseFromSimilar(bestMatch, texto, direccion, similarity, startTime, similarSentences)
            }
        }

        // 2. Si no hay oraciones similares suficientes, palabra por palabra
        return translateWordByWordOnly(texto, direccion, modo, startTime)
    }

    private suspend fun translateWordByWordOnly(
        texto: String,
        direccion: TranslationDirection,
        modo: TranslationMode,
        startTime: Long
    ): TranslationResponse {

        val wordBreakdown = translateWordByWord(texto, direccion)
        val traduccionLiteral = wordBreakdown.joinToString(" ") { it.palabraTraducida }

        // Crear versión más natural si es modo NATURAL
        val traduccionNatural = if (modo == TranslationMode.NATURAL) {
            TranslationUtils.constructNaturalSentence(wordBreakdown, direccion)
        } else {
            traduccionLiteral
        }

        val confidence = TranslationUtils.calculateTranslationConfidence(
            method = TranslationMethod.PALABRA_POR_PALABRA,
            wordsFoundInCorpus = wordBreakdown.count { it.encontradaEnCorpus },
            totalWords = wordBreakdown.size
        )

        return TranslationResponse(
            textoOriginal = texto,
            traduccionNatural = traduccionNatural,
            traduccionLiteral = if (modo == TranslationMode.BOTH) traduccionLiteral else null,
            direccion = direccion,
            metodo = TranslationMethod.PALABRA_POR_PALABRA,
            confianza = confidence,
            tiempoRespuesta = System.currentTimeMillis() - startTime,
            desglosePalabras = wordBreakdown
        )
    }

    // =============================================================================
    // BÚSQUEDAS ESPECÍFICAS
    // =============================================================================

    override suspend fun findExactMatch(
        texto: String,
        direccion: TranslationDirection
    ): SentenceModel? = withContext(Dispatchers.IO) {
        try {
            val field = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                "oracion_espanol" else "oracion_pamiwa"

            val querySnapshot = firestore.collection(COLLECTION_SENTENCES)
                .whereEqualTo(field, texto.lowercase().trim())
                .limit(1)
                .get()
                .await()

            querySnapshot.documents.firstOrNull()?.let { doc ->
                documentToSentenceModel(doc.id, doc.data ?: emptyMap())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en findExactMatch", e)
            null
        }
    }

    override suspend fun findSimilarSentences(
        texto: String,
        direccion: TranslationDirection,
        limit: Int
    ): List<SentenceModel> = withContext(Dispatchers.IO) {
        try {
            val textoLimpio = texto.lowercase().trim()
            val palabras = textoLimpio.split(" ").filter { it.length > 2 }  // Palabras clave simples

            val resultados = mutableSetOf<SentenceModel>()


            val field = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                "oracion_espanol" else "oracion_pamiwa"  //

            // Buscar todas las oraciones y calcular similitud en cliente
            // (Firebase tiene limitaciones para búsquedas complejas)
            val allSentences = firestore.collection(COLLECTION_SENTENCES)
                .limit(100)  // Limitar para performance
                .get()
                .await()

            allSentences.documents.forEach { doc ->
                doc.data?.let { data ->
                    val sentence = documentToSentenceModel(doc.id, data)
                    val textoComparar = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                        sentence.oracionEspanol else sentence.oracionPamiwa  // ✅ Cambiado

                    val similarity = similarityCalculator.calculateSimilarity(
                        textoLimpio,
                        textoComparar.lowercase()
                    )

                    if (similarity >= 0.3f) {  // Umbral mínimo
                        resultados.add(sentence)
                    }
                }
            }

            // Ordenar por similitud y tomar los mejores
            resultados.map { sentence ->
                val textoComparar = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                    sentence.oracionEspanol else sentence.oracionPamiwa

                sentence to similarityCalculator.calculateSimilarity(textoLimpio, textoComparar.lowercase())
            }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }

        } catch (e: Exception) {
            Log.e(TAG, "Error en findSimilarSentences", e)
            emptyList()
        }
    }

    override suspend fun translateWordByWord(
        texto: String,
        direccion: TranslationDirection
    ): List<WordBreakdown> = withContext(Dispatchers.IO) {
        try {
            val palabras = texto.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            val resultados = mutableListOf<WordBreakdown>()

            palabras.forEach { palabra ->
                val palabraLimpia = palabra.lowercase()
                    .replace(Regex("[^a-záéíóúñü]"), "")

                // PASO 1: Buscar en corpus de palabras
                val wordFromCorpus = findWordInNewCorpus(palabraLimpia, direccion)

                if (wordFromCorpus != null) {
                    // ✅ Encontrada en corpus de palabras
                    resultados.add(WordBreakdown(
                        palabraOriginal = palabra,
                        palabraTraducida = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                            wordFromCorpus.palabraPamiwa else wordFromCorpus.palabraEspanol,
                        categoria = wordFromCorpus.categoria,
                        confianza = 0.9f,
                        encontradaEnCorpus = true
                    ))
                } else {
                    // PASO 2: Intentar buscar en colección legacy (compatibilidad)
                    val wordFromLegacy = findWordInLegacyCorpus(palabraLimpia, direccion)

                    if (wordFromLegacy != null) {
                        // ✅ Encontrada en corpus legacy
                        resultados.add(WordBreakdown(
                            palabraOriginal = palabra,
                            palabraTraducida = wordFromLegacy,
                            categoria = "legacy",
                            confianza = 0.85f,
                            encontradaEnCorpus = true
                        ))
                    } else {
                        // PASO 3: NUEVO - Intentar extraer desde oraciones
                        val extractedWord = getCachedExtractedWord(palabraLimpia, direccion)
                            ?: extractWordFromSentences(palabraLimpia, direccion)

                        if (extractedWord != null) {
                            // ✅ Extraída desde oraciones
                            resultados.add(WordBreakdown(
                                palabraOriginal = palabra,
                                palabraTraducida = extractedWord,
                                categoria = "extracted_from_sentence",
                                confianza = 0.7f, // Menor confianza que corpus directo
                                encontradaEnCorpus = false
                            ))

                            Log.d(TAG, "✅ Palabra extraída: '$palabra' → '$extractedWord'")
                        } else {
                            // ❌ No encontrada en ningún lado
                            resultados.add(WordBreakdown(
                                palabraOriginal = palabra,
                                palabraTraducida = palabra, // Mantener original
                                categoria = "unknown",
                                confianza = 0.1f,
                                encontradaEnCorpus = false
                            ))

                            Log.w(TAG, "⚠️ Palabra no encontrada: '$palabra'")
                        }
                    }
                }
            }

            resultados
        } catch (e: Exception) {
            Log.e(TAG, "Error en translateWordByWord", e)
            emptyList()
        }
    }

    // =============================================================================
    // BÚSQUEDAS EN CORPUS
    // =============================================================================

    private suspend fun findWordInNewCorpus(
        palabra: String,
        direccion: TranslationDirection
    ): WordModel? {
        return try {
            val field = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                "palabra_espanol" else "palabra_pamiwa"

            val querySnapshot = firestore.collection(COLLECTION_WORDS)
                .whereEqualTo(field, palabra)
                .limit(1)
                .get()
                .await()

            querySnapshot.documents.firstOrNull()?.let { doc ->
                documentToWordModel(doc.id, doc.data ?: emptyMap())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando palabra: $palabra", e)
            null
        }
    }

    override suspend fun findWordInCorpus(
        palabra: String,
        direccion: TranslationDirection
    ): WordModel? {
        return findWordInNewCorpus(palabra, direccion)
    }

    /**
     * Función de utilidad para analizar qué palabras están siendo extraídas
     * Úsala para debugging y métricas
     */
    suspend fun analyzeWordExtraction(texto: String, direccion: TranslationDirection): ExtractionAnalysis {
        val palabras = texto.split("\\s+".toRegex()).filter { it.isNotEmpty() }

        val stats = ExtractionAnalysis(
            totalPalabras = palabras.size,
            encontradasEnCorpus = 0,
            extraidasDeOraciones = 0,
            noEncontradas = 0,
            detalles = mutableListOf()
        )

        palabras.forEach { palabra ->
            val palabraLimpia = palabra.lowercase().replace(Regex("[^a-záéíóúñü]"), "")

            val enCorpus = findWordInNewCorpus(palabraLimpia, direccion) != null
            val enLegacy = if (!enCorpus) findWordInLegacyCorpus(palabraLimpia, direccion) != null else false
            val extraida = if (!enCorpus && !enLegacy)
                extractWordFromSentences(palabraLimpia, direccion) != null else false

            when {
                enCorpus -> stats.encontradasEnCorpus++
                enLegacy -> stats.encontradasEnCorpus++
                extraida -> stats.extraidasDeOraciones++
                else -> stats.noEncontradas++
            }

            stats.detalles.add(WordExtractionDetail(
                palabra = palabra,
                encontradaEnCorpus = enCorpus || enLegacy,
                extraidaDeOracion = extraida,
                noEncontrada = !enCorpus && !enLegacy && !extraida
            ))
        }

        Log.d(TAG, """
        Análisis de extracción:
        - Total palabras: ${stats.totalPalabras}
        - En corpus: ${stats.encontradasEnCorpus}
        - Extraídas: ${stats.extraidasDeOraciones}
        - No encontradas: ${stats.noEncontradas}
        - Cobertura: ${((stats.encontradasEnCorpus + stats.extraidasDeOraciones) * 100.0 / stats.totalPalabras).toInt()}%
    """.trimIndent())

        return stats
    }

    data class ExtractionAnalysis(
        val totalPalabras: Int,
        var encontradasEnCorpus: Int,
        var extraidasDeOraciones: Int,
        var noEncontradas: Int,
        val detalles: MutableList<WordExtractionDetail>
    )

    data class WordExtractionDetail(
        val palabra: String,
        val encontradaEnCorpus: Boolean,
        val extraidaDeOracion: Boolean,
        val noEncontrada: Boolean
    )

// ============================================================================
// FUNCIÓN PARA LIMPIAR CACHE DE EXTRACCIONES
// ============================================================================

    /**
     * Limpia el cache de palabras extraídas
     * Llámala periódicamente o cuando recargues el corpus
     */
    fun clearExtractedWordsCache() {
        extractedWordsCache.clear()
        Log.d(TAG, "Cache de palabras extraídas limpiado")
    }

    // =============================================================================
    // COMPATIBILIDAD CON TU SISTEMA ACTUAL
    // =============================================================================

    private suspend fun findWordInLegacyCorpus(
        palabra: String,
        direccion: TranslationDirection
    ): String? {
        return try {
            // Buscar en tu colección actual "tu_coleccion"
            val querySnapshot = firestore.collection(COLLECTION_LEGACY)
                .whereEqualTo("palabra_espanol", palabra)
                .limit(1)
                .get()
                .await()

            querySnapshot.documents.firstOrNull()?.getString("palabra_pamiwa")
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando en colección legacy: $palabra", e)
            null
        }
    }

    override suspend fun buscarEnColeccionActual(palabra: String): String? {
        return findWordInLegacyCorpus(palabra, TranslationDirection.ES_TO_PAMIWA)
    }

    override suspend fun buscarTraduccionesCompatible(oracion: String): List<String> {
        return try {
            val result = translateText(
                texto = oracion,
                direccion = TranslationDirection.ES_TO_PAMIWA,
                modo = TranslationMode.NATURAL
            )

            result.fold(
                onSuccess = { response -> response.toCompatibleList() },
                onFailure = { emptyList() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en buscarTraduccionesCompatible", e)
            emptyList()
        }
    }

    // =============================================================================
    // SISTEMA DE CORRECCIONES
    // =============================================================================

    override suspend fun saveUserCorrection(correction: UserCorrectionModel): Result<Boolean> {
        return try {
            val correctionWithDefaults = correction.copy(
                id = "", // Firestore generará el ID
                timestamp = System.currentTimeMillis(),
                aplicadaInmediatamente = true,
                estadoValidacion = ValidationStatus.PENDING
            )

            firestore.collection(COLLECTION_USER_CORRECTIONS)
                .add(correctionWithDefaults)
                .await()

            Log.d(TAG, "Corrección guardada exitosamente")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando corrección", e)
            Result.failure(e)
        }
    }

    override suspend fun getUserCorrection(
        texto: String,
        direccion: TranslationDirection
    ): UserCorrectionModel? {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_USER_CORRECTIONS)
                .whereEqualTo("textoOriginal", texto.lowercase().trim())
                .whereEqualTo("direccion", direccion.name)
                .whereEqualTo("aplicadaInmediatamente", true)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            querySnapshot.documents.firstOrNull()?.let { doc ->
                documentToCorrectionModel(doc.id, doc.data ?: emptyMap())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo corrección del usuario", e)
            null
        }
    }

    override suspend fun getPendingCorrections(): List<UserCorrectionModel> {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_USER_CORRECTIONS)
                .whereEqualTo("estadoValidacion", ValidationStatus.PENDING.name)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            querySnapshot.documents.mapNotNull { doc ->
                doc.data?.let { data ->
                    documentToCorrectionModel(doc.id, data)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo correcciones pendientes", e)
            emptyList()
        }
    }

    override suspend fun markCorrectionAsValidated(
        correctionId: String,
        status: ValidationStatus,
        expertComment: String
    ): Result<Boolean> {
        return try {
            val updates = mapOf(
                "estadoValidacion" to status.name,
                "validadaPorExperto" to true,
                "comentarioExperto" to expertComment
            )

            firestore.collection(COLLECTION_USER_CORRECTIONS)
                .document(correctionId)
                .update(updates)
                .await()

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error marcando corrección como validada", e)
            Result.failure(e)
        }
    }

    override suspend fun applyValidatedCorrections(): Result<Int> {
        // Implementación para aplicar correcciones validadas por el experto
        // Por ahora retorna 0, implementar después si es necesario
        return Result.success(0)
    }

    // =============================================================================
    // GESTIÓN DEL CORPUS
    // =============================================================================

    override suspend fun loadCorpusFromFirebase(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            _corpusLoadingState.value = CorpusLoadingState.LOADING

            // Contar palabras
            val wordsSnapshot = firestore.collection(COLLECTION_EXPERT_WORDS)
                .limit(1)
                .get()
                .await()

            // Contar oraciones
            val sentencesSnapshot = firestore.collection(COLLECTION_EXPERT_SENTENCES)
                .limit(1)
                .get()
                .await()

            // Por ahora solo verificamos que las colecciones existan
            // En el futuro aquí cargarías a base de datos local

            val wordsExist = wordsSnapshot.documents.isNotEmpty()
            val sentencesExist = sentencesSnapshot.documents.isNotEmpty()

            if (wordsExist || sentencesExist) {
                _corpusLoadingState.value = CorpusLoadingState.LOADED
                Log.d(TAG, "Corpus verificado exitosamente")

                // Actualizar estadísticas
                updateCorpusStats()

                Result.success(true)
            } else {
                _corpusLoadingState.value = CorpusLoadingState.EMPTY
                Log.w(TAG, "Corpus vacío")
                Result.success(false)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error cargando corpus", e)
            _corpusLoadingState.value = CorpusLoadingState.ERROR
            Result.failure(e)
        }
    }

    private suspend fun updateCorpusStats() {
        try {
            // Obtener conteos reales
            val wordsSnapshot = firestore.collection(COLLECTION_EXPERT_WORDS).get().await()
            val sentencesSnapshot = firestore.collection(COLLECTION_EXPERT_SENTENCES).get().await()
            val correctionsSnapshot = firestore.collection(COLLECTION_USER_CORRECTIONS)
                .whereEqualTo("estadoValidacion", ValidationStatus.PENDING.name)
                .get()
                .await()

            val stats = CorpusStats(
                totalPalabras = wordsSnapshot.size(),
                totalOraciones = sentencesSnapshot.size(),
                totalCorreccionesPendientes = correctionsSnapshot.size(),
                ultimaActualizacion = System.currentTimeMillis(),
                precisionPromedio = 0.85f // Valor estimado
            )

            _corpusStats.value = stats
            Log.d(TAG, "Stats actualizadas: $stats")
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando stats", e)
        }
    }

    override suspend fun getCorpusStats(): CorpusStats = _corpusStats.value

    override fun getCorpusLoadingState(): Flow<CorpusLoadingState> = _corpusLoadingState.asStateFlow()

    override suspend fun isCorpusReady(): Boolean {
        return _corpusLoadingState.value == CorpusLoadingState.LOADED
    }

    // =============================================================================
    // CACHE Y PERFORMANCE
    // =============================================================================

    override suspend fun getCachedTranslation(
        texto: String,
        direccion: TranslationDirection
    ): TranslationCacheModel? {
        val cacheKey = TranslationUtils.createCacheKey(texto, direccion)
        return memoryCache[cacheKey]?.takeIf { !isCacheExpired(it) }
    }

    override suspend fun cacheTranslation(cache: TranslationCacheModel): Result<Boolean> {
        return try {
            memoryCache[cache.cacheKey] = cache

            // Limpiar cache viejo periódicamente
            if (memoryCache.size > 100) { // Límite arbitrario
                clearExpiredCache()
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error cacheando traducción", e)
            Result.failure(e)
        }
    }

    override suspend fun clearExpiredCache(): Result<Int> {
        return try {
            val currentTime = System.currentTimeMillis()
            val expiredKeys = memoryCache.filterValues { isCacheExpired(it) }.keys

            expiredKeys.forEach { key ->
                memoryCache.remove(key)
            }

            Log.d(TAG, "Cache limpiado: ${expiredKeys.size} entradas eliminadas")
            Result.success(expiredKeys.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando cache", e)
            Result.failure(e)
        }
    }

    override suspend fun getCacheSize(): Int = memoryCache.size

    private fun isCacheExpired(cache: TranslationCacheModel): Boolean {
        return System.currentTimeMillis() > cache.expiraEn
    }

    private fun cacheResult(response: TranslationResponse, cacheKey: String) {
        val cache = TranslationCacheModel(
            cacheKey = cacheKey,
            textoOriginal = response.textoOriginal,
            textoTraducido = response.traduccionNatural,
            direccion = response.direccion,
            metodo = response.metodo,
            confianza = response.confianza,
            timestamp = System.currentTimeMillis(),
            expiraEn = System.currentTimeMillis() + CACHE_DURATION_MS
        )

        memoryCache[cacheKey] = cache
    }

    // =============================================================================
    // MÉTODOS AUXILIARES Y CONVERSIONES
    // =============================================================================

    private fun createResponseFromExactMatch(
        sentence: SentenceModel,
        originalText: String,
        direction: TranslationDirection,
        startTime: Long
    ): TranslationResponse {
        val translation = if (direction == TranslationDirection.ES_TO_PAMIWA)
            sentence.oracionPamiwa else sentence.oracionEspanol

        return TranslationResponse(
            textoOriginal = originalText,
            traduccionNatural = translation,
            direccion = direction,
            metodo = TranslationMethod.COINCIDENCIA_EXACTA,
            confianza = sentence.confianza,
            tiempoRespuesta = System.currentTimeMillis() - startTime
        )
    }

    private fun createResponseFromSimilar(
        sentence: SentenceModel,
        originalText: String,
        direction: TranslationDirection,
        similarity: Float,
        startTime: Long,
        similarSentences: List<SentenceModel>
    ): TranslationResponse {
        val translation = if (direction == TranslationDirection.ES_TO_PAMIWA)
            sentence.oracionPamiwa else sentence.oracionEspanol

        return TranslationResponse(
            textoOriginal = originalText,
            traduccionNatural = translation,
            direccion = direction,
            metodo = TranslationMethod.ORACION_SIMILAR,
            confianza = similarity * sentence.confianza, // Combinar similitud con confianza del experto
            tiempoRespuesta = System.currentTimeMillis() - startTime,
            oracionesSimilares = similarSentences
        )
    }

    private fun createResponseFromCorrection(
        correction: UserCorrectionModel,
        originalText: String,
        startTime: Long
    ): TranslationResponse {
        return TranslationResponse(
            textoOriginal = originalText,
            traduccionNatural = correction.correccionUsuario,
            direccion = correction.direccion,
            metodo = TranslationMethod.USER_CORRECTION,
            confianza = correction.confianzaUsuario,
            tiempoRespuesta = System.currentTimeMillis() - startTime,
            esCorreccionUsuario = true,
            requiereValidacionExperto = !correction.validadaPorExperto
        )
    }

    // Conversiones de documentos Firebase a modelos
    private fun documentToSentenceModel(id: String, data: Map<String, Any>): SentenceModel {
        return SentenceModel(
            id = id,
            oracionEspanol = data["oracion_espanol"] as? String ?: "",
            oracionPamiwa = data["oracion_pamiwa"] as? String ?: "",
            dificultad = data["dificultad"] as? String ?: "medium",
            categoria = data["categoria"] as? String ?: "",
            tags = (data["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            confianza = (data["confianza"] as? Number)?.toFloat() ?: 1.0f,
            expertValidated = data["expert_validated"] as? Boolean ?: true,
            createdAt = (data["created_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun documentToWordModel(id: String, data: Map<String, Any>): WordModel {
        return WordModel(
            id = id,
            palabraEspanol = data["palabra_espanol"] as? String ?: "",
            palabraPamiwa = data["palabra_pamiwa"] as? String ?: "",
            categoria = data["categoria"] as? String ?: "",
            frecuencia = (data["frecuencia"] as? Number)?.toInt() ?: 0,
            confianza = (data["confianza"] as? Number)?.toFloat() ?: 1.0f,
            expertValidated = data["expert_validated"] as? Boolean ?: true,
            createdAt = (data["created_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun documentToCorrectionModel(id: String, data: Map<String, Any>): UserCorrectionModel {
        return UserCorrectionModel(
            id = id,
            textoOriginal = data["textoOriginal"] as? String ?: "",
            traduccionIA = data["traduccionIA"] as? String ?: "",
            correccionUsuario = data["correccionUsuario"] as? String ?: "",
            direccion = TranslationDirection.valueOf(
                data["direccion"] as? String ?: "ES_TO_PAMIWA"
            ),
            metodoOriginal = TranslationMethod.valueOf(
                data["metodoOriginal"] as? String ?: "WORD_BY_WORD"
            ),
            confianzaOriginal = (data["confianzaOriginal"] as? Number)?.toFloat() ?: 0f,
            aplicadaInmediatamente = data["aplicadaInmediatamente"] as? Boolean ?: true,
            validadaPorExperto = data["validadaPorExperto"] as? Boolean ?: false,
            estadoValidacion = ValidationStatus.valueOf(
                data["estadoValidacion"] as? String ?: "PENDING"
            ),
            comentarioExperto = data["comentarioExperto"] as? String ?: "",
            confianzaUsuario = (data["confianzaUsuario"] as? Number)?.toFloat() ?: 0.7f,
            usuarioId = data["usuarioId"] as? String ?: "anonymous",
            timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            numeroReportes = (data["numeroReportes"] as? Number)?.toInt() ?: 0
        )
    }

    // Extension para convertir cache a response
    private fun TranslationCacheModel.toTranslationResponse(startTime: Long): TranslationResponse {
        return TranslationResponse(
            textoOriginal = this.textoOriginal,
            traduccionNatural = this.textoTraducido,
            direccion = this.direccion,
            metodo = this.metodo,
            confianza = this.confianza,
            tiempoRespuesta = System.currentTimeMillis() - startTime,
            esDesdCache = true
        )
    }

    // Extension para convertir response a lista compatible
    private fun TranslationResponse.toCompatibleList(): List<String> {
        return if (this.traduccionLiteral != null && this.traduccionLiteral != this.traduccionNatural) {
            listOf(this.traduccionNatural, this.traduccionLiteral)
        } else {
            listOf(this.traduccionNatural)
        }
    }

    // =============================================================================
    // MÉTODOS PARA ANÁLISIS Y REPORTES (Implementación básica)
    // =============================================================================

    override suspend fun translateWord(
        palabra: String,
        direccion: TranslationDirection
    ): Result<WordBreakdown> {
        return try {
            val breakdown = translateWordByWord(palabra, direccion)
            if (breakdown.isNotEmpty()) {
                Result.success(breakdown.first())
            } else {
                Result.failure(Exception("Palabra no encontrada: $palabra"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun translateSentence(
        oracion: String,
        direccion: TranslationDirection
    ): Result<TranslationResponse> {
        return translateText(oracion, direccion, TranslationMode.NATURAL)
    }

    override suspend fun getUsageMetrics(): TranslationMetrics {
        // Implementación básica - en el futuro podrías trackear métricas reales
        return TranslationMetrics(
            totalTranslations = 0,
            exactMatches = 0,
            similarMatches = 0,
            wordByWordTranslations = 0,
            userCorrections = 0,
            averageConfidence = 0.8f,
            averageResponseTime = 500L,
            mostTranslatedWords = emptyList(),
            mostTranslatedSentences = emptyList()
        )
    }

    override suspend fun reportTranslationError(
        originalText: String,
        translation: String,
        errorType: String
    ): Result<Boolean> {
        return try {
            val errorReport = mapOf(
                "originalText" to originalText,
                "translation" to translation,
                "errorType" to errorType,
                "timestamp" to System.currentTimeMillis(),
                "deviceInfo" to "Android" // Podrías agregar más info del dispositivo
            )

            firestore.collection("translation_errors")
                .add(errorReport)
                .await()

            Log.d(TAG, "Error reportado: $errorType para '$originalText'")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error reportando error de traducción", e)
            Result.failure(e)
        }
    }



    /**
     * NUEVA FUNCIÓN: Extrae palabras individuales desde oraciones
     * Se ejecuta cuando no se encuentra la palabra en tu_coleccion
     */
    private suspend fun extractWordFromSentences(
        palabra: String,
        direccion: TranslationDirection
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Intentando extraer '$palabra' desde oraciones...")

            val palabraLimpia = palabra.lowercase().trim()
            val field = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                "oracion_espanol" else "oracion_pamiwa"

            // Buscar oraciones que contengan esta palabra
            val oracionesConPalabra = firestore.collection(COLLECTION_SENTENCES)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    doc.data?.let { data ->
                        val oracion = data[field] as? String
                        if (oracion?.lowercase()?.contains(palabraLimpia) == true) {
                            documentToSentenceModel(doc.id, data)
                        } else null
                    }
                }

            if (oracionesConPalabra.isEmpty()) {
                Log.d(TAG, "No se encontraron oraciones con '$palabra'")
                return@withContext null
            }

            Log.d(TAG, "Encontradas ${oracionesConPalabra.size} oraciones con '$palabra'")

            // Intentar extraer la traducción usando alineación palabra por palabra
            val traduccionExtraida = extractWordUsingAlignment(
                palabra = palabraLimpia,
                oraciones = oracionesConPalabra,
                direccion = direccion
            )

            if (traduccionExtraida != null) {
                Log.d(TAG, "Palabra extraída exitosamente: '$palabra' → '$traduccionExtraida'")

                // Opcional: Cachear esta extracción para futuro uso
                cacheExtractedWord(palabraLimpia, traduccionExtraida, direccion)
            }

            traduccionExtraida

        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo palabra desde oraciones", e)
            null
        }
    }

    /**
     * Extrae palabra usando alineación simple basada en posición
     */
    private fun extractWordUsingAlignment(
        palabra: String,
        oraciones: List<SentenceModel>,
        direccion: TranslationDirection
    ): String? {

        val palabrasCandidatas = mutableMapOf<String, Int>() // palabra → frecuencia

        for (oracion in oraciones) {
            val textoOrigen = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                oracion.oracionEspanol else oracion.oracionPamiwa
            val textoDestino = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                oracion.oracionPamiwa else oracion.oracionEspanol

            // Dividir en palabras
            val palabrasOrigen = textoOrigen.lowercase()
                .replace(Regex("[^a-záéíóúñü\\s]"), "")
                .split("\\s+".toRegex())
                .filter { it.isNotEmpty() }

            val palabrasDestino = textoDestino.lowercase()
                .replace(Regex("[^a-záéíóúñüɨɵɛ\\s~]"), "")
                .split("\\s+".toRegex())
                .filter { it.isNotEmpty() }

            // Encontrar posición de la palabra en el origen
            val posicion = palabrasOrigen.indexOfFirst {
                it.contains(palabra) || palabra.contains(it)
            }

            if (posicion != -1 && posicion < palabrasDestino.size) {
                // La palabra en esa posición en el destino es candidata
                val candidata = palabrasDestino[posicion]
                palabrasCandidatas[candidata] = palabrasCandidatas.getOrDefault(candidata, 0) + 1
            }
        }

        // Retornar la palabra más frecuente
        return palabrasCandidatas.maxByOrNull { it.value }?.key
    }

    /**
     * Cache simple para palabras extraídas
     */
    private val extractedWordsCache = mutableMapOf<String, String>()

    private fun cacheExtractedWord(palabra: String, traduccion: String, direccion: TranslationDirection) {
        val key = "${palabra}_${direccion.name}"
        extractedWordsCache[key] = traduccion
    }

    private fun getCachedExtractedWord(palabra: String, direccion: TranslationDirection): String? {
        val key = "${palabra}_${direccion.name}"
        return extractedWordsCache[key]
    }





}

// =============================================================================
// EXTENSION FUNCTIONS PARA IMPORTS
// =============================================================================

// Estas extensions van al final del archivo o en un archivo separado
// si prefieres mantener el código más organizado

/**
 * Extension para compatibilidad con TextUtils
 */
private fun String.extractKeywords(maxKeywords: Int = 3): List<String> {
    return TextUtils.run { this@extractKeywords.extractKeywords(maxKeywords) }
}

/**
 * Extension para compatibilidad con TranslationUtils
 */
private fun TranslationResponse.toCompatibleList(): List<String> {
    return TranslationUtils.run {
        if (this@toCompatibleList.traduccionLiteral != null &&
            this@toCompatibleList.traduccionLiteral != this@toCompatibleList.traduccionNatural) {
            listOf(this@toCompatibleList.traduccionNatural, this@toCompatibleList.traduccionLiteral!!)
        } else {
            listOf(this@toCompatibleList.traduccionNatural)
        }
    }
}



*/
