package com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel

import androidx.lifecycle.ViewModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.TranslationUIState
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EnhancedTranslationViewModel @Inject constructor(
    private val translationRepository: TranslationRepository
) : ViewModel() {

    companion object {
        private const val TAG = "EnhancedTranslationVM"
        private const val DEBOUNCE_DELAY = 500L
    }

    // =============================================================================
    // ESTADOS PRIVADOS
    // =============================================================================

    private val _uiState = MutableStateFlow(TranslationUIState())
    val uiState: StateFlow<TranslationUIState> = _uiState.asStateFlow()

    // Estados derivados para compatibilidad con tu código actual
    val text: StateFlow<String> = _uiState.map { it.textoInput }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    val traducciones: StateFlow<List<String>> = _uiState.map { state ->
        state.resultado?.let { listOf(it.traduccionNatural) } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // ✅ SOLUCIÓN: StateFlow independiente para selector
    private val _selector = MutableStateFlow(false)
    val selector: StateFlow<Boolean> = _selector.asStateFlow()

    // Estado del corpus
    val corpusLoadingState = translationRepository.getCorpusLoadingState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CorpusLoadingState.LOADING
        )

    // Job para controlar traducción con debounce
    private var translationJob: Job? = null

    // =============================================================================
    // INICIALIZACIÓN
    // =============================================================================

    init {
        initializeRepository()
        observeCorpusState()
    }

    private fun initializeRepository() {
        viewModelScope.launch {
            try {
                val result = translationRepository.loadCorpusFromFirebase()
                if (result.isSuccess) {
                    Log.d(TAG, "Repository inicializado correctamente")
                } else {
                    updateError("Error inicializando sistema de traducción")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en inicialización", e)
                updateError("Error de inicialización: ${e.message}")
            }
        }
    }

    private fun observeCorpusState() {
        viewModelScope.launch {
            corpusLoadingState.collect { state ->
                _uiState.value = _uiState.value.copy(estadoCorpus = state)

                when (state) {
                    CorpusLoadingState.ERROR -> {
                        updateError("Error cargando corpus de traducciones")
                    }
                    CorpusLoadingState.EMPTY -> {
                        updateError("Corpus de traducciones vacío")
                    }
                    else -> {
                        // Limpiar error si había
                        if (_uiState.value.error != null) {
                            _uiState.value = _uiState.value.copy(error = null)
                        }
                    }
                }
            }
        }
    }

    // =============================================================================
    // MÉTODOS PÚBLICOS PARA LA UI
    // =============================================================================

    /**
     * Actualiza el texto de entrada y realiza traducción con debounce
     */
    fun updateInputText(newText: String) {
        _uiState.value = _uiState.value.copy(
            textoInput = newText,
            error = null
        )

        // Cancelar traducción anterior
        translationJob?.cancel()

        if (newText.trim().isEmpty()) {
            clearTranslation()
            return
        }

        // Iniciar nueva traducción con debounce
        translationJob = viewModelScope.launch {
            delay(DEBOUNCE_DELAY)
            performTranslation(newText.trim())
        }
    }

    /**
     * Cambia la dirección de traducción (Español ↔ Pamiwa)
     */
    fun toggleTranslationDirection() {
        val currentDirection = _uiState.value.direccion
        val newDirection = if (currentDirection == TranslationDirection.ES_TO_PAMIWA) {
            TranslationDirection.PAMIWA_TO_ES
        } else {
            TranslationDirection.ES_TO_PAMIWA
        }

        // ✅ Actualizar ambos estados de manera sincronizada
        _uiState.value = _uiState.value.copy(direccion = newDirection)
        _selector.value = (newDirection == TranslationDirection.PAMIWA_TO_ES)

        Log.d(TAG, "Dirección cambiada a: $newDirection")

        // Re-traducir si hay texto
        val currentText = _uiState.value.textoInput
        if (currentText.isNotEmpty()) {
            viewModelScope.launch {
                performTranslation(currentText)
            }
        }
    }

    /**
     * Cambia el modo de traducción (Natural, Literal, Ambos)
     */
    fun setTranslationMode(mode: TranslationMode) {
        _uiState.value = _uiState.value.copy(modo = mode)

        Log.d(TAG, "Modo de traducción cambiado a: $mode")

        // Re-traducir con el nuevo modo si hay texto
        val currentText = _uiState.value.textoInput
        if (currentText.isNotEmpty()) {
            viewModelScope.launch {
                performTranslation(currentText)
            }
        }
    }

    /**
     * Muestra/oculta el desglose palabra por palabra
     */
    fun toggleWordBreakdown() {
        _uiState.value = _uiState.value.copy(
            mostrarDesglose = !_uiState.value.mostrarDesglose
        )
    }

    /**
     * Muestra/oculta las oraciones similares
     */
    fun toggleSimilarSentences() {
        _uiState.value = _uiState.value.copy(
            mostrarSimilares = !_uiState.value.mostrarSimilares
        )
    }

    /**
     * Guarda una corrección del usuario (Sistema híbrido)
     */
    fun submitUserCorrection(correctedText: String) {
        val currentState = _uiState.value
        val currentResult = currentState.resultado ?: return

        if (correctedText.trim().isEmpty()) {
            updateError("La corrección no puede estar vacía")
            return
        }

        if (correctedText.trim() == currentResult.traduccionNatural.trim()) {
            updateError("La corrección es igual a la traducción actual")
            return
        }

        viewModelScope.launch {
            try {
                val correction = UserCorrectionModel(
                    textoOriginal = currentResult.textoOriginal,
                    traduccionIA = currentResult.traduccionNatural,
                    correccionUsuario = correctedText.trim(),
                    direccion = currentResult.direccion,
                    metodoOriginal = currentResult.metodo,
                    confianzaOriginal = currentResult.confianza,
                    aplicadaInmediatamente = true,
                    validadaPorExperto = false,
                    estadoValidacion = ValidationStatus.PENDING,
                    confianzaUsuario = 0.8f, // Alta confianza en correcciones de usuario
                    usuarioId = "user_${System.currentTimeMillis()}", // ID básico
                    timestamp = System.currentTimeMillis()
                )

                val result = translationRepository.saveUserCorrection(correction)

                if (result.isSuccess) {
                    // Actualizar el resultado mostrado con la corrección
                    _uiState.value = _uiState.value.copy(
                        resultado = currentResult.copy(
                            traduccionNatural = correctedText.trim(),
                            metodo = TranslationMethod.USER_CORRECTION,
                            confianza = 0.9f, // Alta confianza en corrección del usuario
                            esCorreccionUsuario = true,
                            requiereValidacionExperto = true
                        )
                    )

                    Log.d(TAG, "Corrección guardada exitosamente: '$correctedText'")
                } else {
                    updateError("Error guardando corrección")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando corrección", e)
                updateError("Error guardando corrección: ${e.message}")
            }
        }
    }

    /**
     * Limpia la traducción actual
     */
    fun clearTranslation() {
        _uiState.value = _uiState.value.copy(
            resultado = null,
            error = null,
            traduciendo = false
        )
    }

    /**
     * Limpia el texto de entrada y la traducción
     */
    fun clearInput() {
        translationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            textoInput = "",
            resultado = null,
            error = null,
            traduciendo = false
        )
    }

    /**
     * Recarga el corpus desde Firebase
     */
    fun reloadCorpus() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(estadoCorpus = CorpusLoadingState.LOADING)

                val result = translationRepository.loadCorpusFromFirebase()

                if (result.isSuccess) {
                    Log.d(TAG, "Corpus recargado exitosamente")
                } else {
                    updateError("Error recargando corpus")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recargando corpus", e)
                updateError("Error recargando corpus: ${e.message}")
            }
        }
    }

    /**
     * Traduce inmediatamente sin debounce (para botones)
     */
    fun translateImmediately() {
        val currentText = _uiState.value.textoInput.trim()
        if (currentText.isNotEmpty()) {
            translationJob?.cancel()
            viewModelScope.launch {
                performTranslation(currentText)
            }
        }
    }

    // =============================================================================
    // MÉTODOS DE COMPATIBILIDAD (Para tu código actual)
    // =============================================================================

    /**
     * Método de compatibilidad con tu ViewModel actual
     */
    fun guardarPalabra(newText: String) {
        updateInputText(newText)
    }

    /**
     * Método de compatibilidad con tu ViewModel actual
     */
    fun buscarTraducciones(oracion: String) {
        updateInputText(oracion)
    }

    /**
     * Método de compatibilidad con tu ViewModel actual
     */
    fun limpiarTraducciones() {
        clearTranslation()
    }

    /**
     * Método de compatibilidad para el selector
     */
    fun toggleSelector() {

        toggleTranslationDirection()
    }

    // =============================================================================
    // MÉTODOS PRIVADOS
    // =============================================================================

    private suspend fun performTranslation(text: String) {
        try {
            _uiState.value = _uiState.value.copy(
                traduciendo = true,
                error = null
            )

            Log.d(TAG, "Iniciando traducción: '$text'")

            val result = translationRepository.translateText(
                texto = text,
                direccion = _uiState.value.direccion,
                modo = _uiState.value.modo
            )

            result.fold(
                onSuccess = { translationResponse ->
                    _uiState.value = _uiState.value.copy(
                        resultado = translationResponse,
                        traduciendo = false
                    )

                    Log.d(TAG, "Traducción exitosa: ${translationResponse.metodo} - " +
                            "Confianza: ${translationResponse.confianza} - " +
                            "Resultado: '${translationResponse.traduccionNatural}'")
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        traduciendo = false,
                        error = "Error en traducción: ${exception.message}"
                    )
                    Log.e(TAG, "Error en traducción", exception)
                }
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                traduciendo = false,
                error = "Error inesperado: ${e.message}"
            )
            Log.e(TAG, "Error inesperado en performTranslation", e)
        }
    }

    private fun updateError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
        Log.w(TAG, "Error actualizado: $message")
    }

    // =============================================================================
    // MÉTODOS PARA ANÁLISIS Y MÉTRICAS (Futuro)
    // =============================================================================

    /**
     * Obtiene estadísticas del corpus
     */
    fun getCorpusStats() {
        viewModelScope.launch {
            try {
                val stats = translationRepository.getCorpusStats()
                Log.d(TAG, "Stats del corpus: $stats")
                // Podrías emitir esto a un StateFlow si necesitas mostrarlo en UI
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo stats", e)
            }
        }
    }

    /**
     * Reporta un error en una traducción
     */
    fun reportTranslationError(errorType: String) {
        val currentResult = _uiState.value.resultado ?: return

        viewModelScope.launch {
            try {
                translationRepository.reportTranslationError(
                    originalText = currentResult.textoOriginal,
                    translation = currentResult.traduccionNatural,
                    errorType = errorType
                )
                Log.d(TAG, "Error reportado: $errorType")
            } catch (e: Exception) {
                Log.e(TAG, "Error reportando error", e)
            }
        }
    }

    /**
     * Limpia cache para liberar memoria
     */
    fun clearCache() {
        viewModelScope.launch {
            try {
                val result = translationRepository.clearExpiredCache()
                result.fold(
                    onSuccess = { cleared ->
                        Log.d(TAG, "Cache limpiado: $cleared entradas eliminadas")
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error limpiando cache", exception)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error en clearCache", e)
            }
        }
    }

    // =============================================================================
    // LIFECYCLE
    // =============================================================================

    override fun onCleared() {
        super.onCleared()
        translationJob?.cancel()
        translationRepository.stopRealtimeSync()
        Log.d(TAG, "ViewModel limpiado")
    }
}

// =============================================================================
// EXTENSION FUNCTIONS PARA UI
// =============================================================================

/**
 * Extension para verificar si el estado permite traducir
 */
fun TranslationUIState.canTranslate(): Boolean {
    return textoInput.isNotBlank() &&
            !traduciendo &&
            estadoCorpus == CorpusLoadingState.LOADED
}

/**
 * Extension para obtener el color de confianza
 */
fun TranslationResponse.getConfidenceColor(): androidx.compose.ui.graphics.Color {
    return when {
        confianza >= 0.8f -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Verde
        confianza >= 0.6f -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Naranja
        confianza >= 0.4f -> androidx.compose.ui.graphics.Color(0xFFFFC107) // Amarillo
        else -> androidx.compose.ui.graphics.Color(0xFFF44336) // Rojo
    }
}

/**
 * Extension para obtener texto descriptivo del método
 */
fun TranslationMethod.getDisplayName(): String {
    return when (this) {
        TranslationMethod.COINCIDENCIA_EXACTA -> "Coincidencia exacta"
        TranslationMethod.ORACION_SIMILAR -> "Oración similar"
        TranslationMethod.PALABRA_POR_PALABRA -> "Palabra por palabra"
        TranslationMethod.HYBRID_AI -> "IA con contexto"
        TranslationMethod.USER_CORRECTION -> "Corrección del usuario"
        TranslationMethod.CONTEXTUAL_SEARCH -> "Búsqueda contextual"
    }
}

/**
 * Extension para verificar si necesita validación del experto
 */
fun TranslationResponse.needsExpertValidation(): Boolean {
    return requiereValidacionExperto ||
            (metodo == TranslationMethod.USER_CORRECTION && !esCorreccionUsuario) ||
            confianza < 0.6f
}