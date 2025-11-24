package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.implementar

import android.content.ContentValues
import android.content.ContentValues.TAG
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore


import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.LocalDataSource
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.CacheTraduccionApiEntity
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.toSentenceModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.toWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val similarityCalculator: SimilarityCalculator,
    private val mBartApiService: MBartApiService,
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
        // 🔴 CAMBIAR A ESTO:
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Primero verificar si hay datos
                val metadata = localDataSource.getSyncMetadata("palabras")

                if (metadata == null || metadata.total_records == 0) {
                    // Primera sincronización completa
                    Log.d(TAG, "📥 Primera sincronización...")
                    syncManager.performFullSync()

                    // Esperar a que termine
                    delay(3000)
                }

                // 2. Después iniciar listeners
                Log.d(TAG, "🔄 Iniciando listeners...")
                syncManager.startRealtimeSync()

            } catch (e: Exception) {
                Log.e(TAG, "Error en inicialización", e)
            }
        }
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

            val esPalabra = textoLimpio.split(" ").size == 1

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

            // 3. Buscar en caché de API (Room)
            val cacheApi = localDataSource.buscarEnCacheApi(textoLimpio, direccion.name)
            if (cacheApi != null) {
                Log.d(TAG, "✅ Encontrado en caché de API")
                return@withContext Result.success(
                    createResponseFromCacheApi(cacheApi, textoLimpio, direccion, startTime)
                )
            }

            // 4. SI ES PALABRA: Buscar en Room o usar API
            if (esPalabra) {
                val palabraEntity = if (direccion == TranslationDirection.ES_TO_PAMIWA) {
                    localDataSource.findPalabraByEspanol(textoLimpio.lowercase())
                } else {
                    localDataSource.findPalabraByPamie(textoLimpio.lowercase())
                }

                if (palabraEntity != null) {
                    // Palabra encontrada en Room
                    val traduccion = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                        palabraEntity.palabra_pamie else palabraEntity.palabra_espanol

                    return@withContext Result.success(
                        TranslationResponse(
                            textoOriginal = textoLimpio,
                            traduccionNatural = traduccion,
                            direccion = direccion,
                            metodo = TranslationMethod.COINCIDENCIA_EXACTA,
                            confianza = palabraEntity.confianza,
                            tiempoRespuesta = System.currentTimeMillis() - startTime
                        )
                    )
                } else {
                    // Palabra NO encontrada → Llamar API
                    Log.d(TAG, "🌐 Palabra no encontrada, llamando API mBART...")
                    return@withContext traducirConApi(textoLimpio, direccion, true, startTime)
                }
            }

            // 5. SI ES ORACIÓN: Buscar muy similares (95%+) y crear híbrido
            val similarSentences = findSimilarSentences(textoLimpio, direccion, limit = 3)
            if (similarSentences.isNotEmpty()) {
                val bestMatch = similarSentences.first()
                val similarity = calculateSimilarity(textoLimpio, bestMatch, direccion)

                // Solo si es 95%+ similar
                if (similarity >= 0.95f) {
                    Log.d(TAG, "✅ Similar ${(similarity * 100).toInt()}%")

                    val traduccionHibrida = createHybridTranslation(textoLimpio, bestMatch, direccion)

                    if (traduccionHibrida != null) {
                        Log.d(TAG, "✅ Híbrido: $traduccionHibrida")

                        return@withContext Result.success(
                            TranslationResponse(
                                textoOriginal = textoLimpio,
                                traduccionNatural = traduccionHibrida,
                                direccion = direccion,
                                metodo = TranslationMethod.ORACION_SIMILAR,
                                confianza = similarity * 0.9f,
                                tiempoRespuesta = System.currentTimeMillis() - startTime,
                                oracionesSimilares = similarSentences
                            )
                        )
                    } else {
                        Log.d(TAG, "⚠️ Híbrido falló, usando API")
                    }
                } else {
                    Log.d(TAG, "⚠️ Solo ${(similarity * 100).toInt()}%, directo a API")
                }
            }

            // 6. Oración NO encontrada → Llamar API
            Log.d(TAG, "🌐 Oración no encontrada, llamando API mBART...")
            traducirConApi(textoLimpio, direccion, false, startTime)

        } catch (e: Exception) {
            Log.e(TAG, "Error en translateText", e)
            Result.failure(e)
        }
    }

    private suspend fun traducirConApi(
        texto: String,
        direccion: TranslationDirection,
        esPalabra: Boolean,
        startTime: Long
    ): Result<TranslationResponse> = withContext(Dispatchers.IO) {
        try {
            // Request simplificado (solo texto)
            val request = TraduccionApiRequest(texto = texto)

            Log.d(TAG, "📡 Llamando API Nekamui: $texto")

            val response = mBartApiService.traducir(request = request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.exito) {
                    Log.d(TAG, "✅ API respondió: ${body.traduccion}")

                    // Guardar en caché
                    val cacheEntity = CacheTraduccionApiEntity(
                        textoOriginal = texto,
                        traduccion = body.traduccion,
                        direccion = direccion.name,
                        esPalabra = esPalabra,
                        confianza = 0.95f,
                        timestamp = System.currentTimeMillis()
                    )
                    localDataSource.guardarCacheApi(cacheEntity)

                    // Crear respuesta
                    val translationResponse = TranslationResponse(
                        textoOriginal = texto,
                        traduccionNatural = body.traduccion,
                        direccion = direccion,
                        metodo = TranslationMethod.HYBRID_AI,
                        confianza = 0.95f,
                        tiempoRespuesta = System.currentTimeMillis() - startTime,
                        esDesdCache = false
                    )

                    return@withContext Result.success(translationResponse)
                } else {
                    Log.e(TAG, "❌ API error: ${body.error}")
                    return@withContext Result.failure(
                        Exception("Error API: ${body.error}")
                    )
                }
            } else {
                Log.e(TAG, "❌ Error HTTP ${response.code()}: ${response.message()}")
                return@withContext Result.failure(
                    Exception("HTTP ${response.code()}: ${response.message()}")
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción en traducirConApi", e)

            // Fallback: traducción palabra por palabra
            if (!esPalabra) {
                Log.d(TAG, "🔄 Fallback a palabra por palabra")
                return@withContext try {
                    val words = translateWordByWord(texto, direccion)
                    val traduccion = words.joinToString(" ") { it.palabraTraducida }

                    Result.success(
                        TranslationResponse(
                            textoOriginal = texto,
                            traduccionNatural = traduccion,
                            direccion = direccion,
                            metodo = TranslationMethod.PALABRA_POR_PALABRA,
                            confianza = 0.5f,
                            tiempoRespuesta = System.currentTimeMillis() - startTime,
                            desglosePalabras = words
                        )
                    )
                } catch (fallbackError: Exception) {
                    Result.failure(fallbackError)
                }
            }

            return@withContext Result.failure(e)
        }
    }


    /**
     * Crea traducción híbrida usando oración similar + palabras del diccionario
     */
    private suspend fun createHybridTranslation(
        textoOriginal: String,
        oracionSimilar: SentenceModel,
        direccion: TranslationDirection
    ): String? = withContext(Dispatchers.IO) {
        try {
            val textoSimilarOrigen = if (direccion == TranslationDirection.ES_TO_PAMIWA) {
                oracionSimilar.oracionEspanol
            } else {
                oracionSimilar.oracionPamie
            }

            var traduccionBase = if (direccion == TranslationDirection.ES_TO_PAMIWA) {
                oracionSimilar.oracionPamie
            } else {
                oracionSimilar.oracionEspanol
            }

            val diferencias = findDifferentWords(textoOriginal, textoSimilarOrigen)

            if (diferencias.isEmpty()) {
                return@withContext traduccionBase
            }

            Log.d(ContentValues.TAG, "🔍 Diferencias: $diferencias")

            for ((palabraOriginal, palabraSimilar) in diferencias) {
                val palabraEntity = if (direccion == TranslationDirection.ES_TO_PAMIWA) {
                    localDataSource.findPalabraByEspanol(palabraOriginal)
                } else {
                    localDataSource.findPalabraByPamie(palabraOriginal)
                }

                if (palabraEntity == null) {
                    Log.d(ContentValues.TAG, "❌ '$palabraOriginal' no en diccionario")
                    return@withContext null
                }

                val traduccionPalabra = if (direccion == TranslationDirection.ES_TO_PAMIWA) {
                    palabraEntity.palabra_pamie
                } else {
                    palabraEntity.palabra_espanol
                }

                val palabraSimilarEntity = if (direccion == TranslationDirection.ES_TO_PAMIWA) {
                    localDataSource.findPalabraByEspanol(palabraSimilar)
                } else {
                    localDataSource.findPalabraByPamie(palabraSimilar)
                }

                if (palabraSimilarEntity != null) {
                    val traduccionSimilar = if (direccion == TranslationDirection.ES_TO_PAMIWA) {
                        palabraSimilarEntity.palabra_pamie
                    } else {
                        palabraSimilarEntity.palabra_espanol
                    }

                    traduccionBase = traduccionBase.replace(
                        traduccionSimilar,
                        traduccionPalabra,
                        ignoreCase = true
                    )

                    Log.d(ContentValues.TAG, "✅ Reemplazo: '$traduccionSimilar' → '$traduccionPalabra'")
                }
            }

            traduccionBase

        } catch (e: Exception) {
            Log.e(ContentValues.TAG, "Error en híbrido", e)
            null
        }
    }

    private fun createResponseFromCacheApi(
        cache: CacheTraduccionApiEntity,
        originalText: String,
        direction: TranslationDirection,
        startTime: Long
    ): TranslationResponse {
        return TranslationResponse(
            textoOriginal = originalText,
            traduccionNatural = cache.traduccion,
            direccion = direction,
            metodo = TranslationMethod.HYBRID_AI,
            confianza = cache.confianza,
            tiempoRespuesta = System.currentTimeMillis() - startTime
        )
    }
    // =============================================================================
    // TRADUCCIÓN PRINCIPAL - AHORA USA ROOM
    // =============================================================================

   /* override suspend fun translateText(
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
    }*/

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
                localDataSource.findOracionExactaPamie(texto.lowercase().trim())  // 🔴 CAMBIADO
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
                val oraciones = localDataSource.searchOracionesByKeywordEspanol(palabra)
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
                    localDataSource.findPalabraByPamie(palabraLimpia)
                }

                if (palabraEntity != null) {
                    // Encontrada en corpus local
                    resultados.add(WordBreakdown(
                        palabraOriginal = palabra,
                        palabraTraducida = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                            palabraEntity.palabra_pamie else palabraEntity.palabra_espanol,
                        categoria = palabraEntity.tipo_palabra ?: "general",
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
            // Buscar oraciones que contengan esta palabra según la dirección
            val oraciones = if (direccion == TranslationDirection.ES_TO_PAMIWA) {
                localDataSource.searchOracionesByKeywordEspanol(palabra)
            } else {
                localDataSource.searchOracionesByKeywordPamie(palabra)
            }

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
                oracion.oracionEspanol else oracion.oracionPamie
            val textoDestino = if (direccion == TranslationDirection.ES_TO_PAMIWA)
                oracion.oracionPamie else oracion.oracionEspanol

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
            sentence.oracionEspanol else sentence.oracionPamie

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
            sentence.oracionPamie else sentence.oracionEspanol

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
            sentence.oracionPamie else sentence.oracionEspanol

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

    override fun stopRealtimeSync() {
        syncManager.stopRealtimeSync()
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
            localDataSource.findPalabraByPamie(palabra)
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
/**
 * Compara dos oraciones y encuentra las palabras diferentes
 */
private fun findDifferentWords(
    textoOriginal: String,
    textoSimilar: String
): List<Pair<String, String>> {
    val palabrasOriginal = textoOriginal.lowercase().trim()
        .replace(Regex("[^a-záéíóúñüɨ\\s]"), "")
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }

    val palabrasSimilar = textoSimilar.lowercase().trim()
        .replace(Regex("[^a-záéíóúñüɨ\\s]"), "")
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }

    val diferencias = mutableListOf<Pair<String, String>>()

    val maxLen = maxOf(palabrasOriginal.size, palabrasSimilar.size)

    for (i in 0 until maxLen) {
        val palabraOrig = palabrasOriginal.getOrNull(i) ?: ""
        val palabraSim = palabrasSimilar.getOrNull(i) ?: ""

        if (palabraOrig != palabraSim && palabraOrig.isNotBlank()) {
            diferencias.add(Pair(palabraOrig, palabraSim))
        }
    }

    return diferencias
}


