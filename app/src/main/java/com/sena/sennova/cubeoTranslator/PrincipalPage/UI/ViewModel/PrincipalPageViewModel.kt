package com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import javax.inject.Inject

@HiltViewModel
class PrincipalPageViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    // Selector de idioma
    private val _selector = MutableLiveData(false)
    val selector: LiveData<Boolean> get() = _selector

    // Palabra o texto ingresado por el usuario
    private val _text = MutableLiveData<String>()
    val text: LiveData<String> get() = _text

    // Traducciones obtenidas
    private val _traducciones = MutableLiveData<List<String>>(emptyList())
    val traducciones: LiveData<List<String>> get() = _traducciones

    // Estado del teclado (visible/oculto)
    private val _isKeyboardVisible = MutableLiveData(false)
    val isKeyboardVisible: LiveData<Boolean> get() = _isKeyboardVisible

    // Estado de error (opcional para mostrar mensajes al usuario)
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    /**
     * Muestra u oculta el teclado.
     */
    fun toggleKeyboardVisibility() {
        _isKeyboardVisible.value = _isKeyboardVisible.value != true
    }

    /**
     * Guarda la palabra o texto ingresado por el usuario.
     */
    fun guardarPalabra(newText: String) {
        _text.value = newText
    }

    /**
     * Limpia las traducciones almacenadas.
     */
    fun limpiarTraducciones() {
        _traducciones.value = emptyList()
    }

    /**
     * Busca traducciones en Firestore para una oración o palabra.
     */
    fun buscarTraducciones(oracion: String) {
        if (oracion.isEmpty()) {
            _traducciones.value = emptyList()
            Log.d("BuscarTraducciones", "La oración está vacía. Limpiando traducciones.")
            return
        }

        // Dividir las palabras
        val palabras = oracion.split(Regex("[\\s,.!?¡¿]+")).filter { it.isNotEmpty() }
        Log.d("BuscarTraducciones", "Palabras a traducir: $palabras")

        val traduccionesList = MutableList(palabras.size) { "" }

        // Iterar sobre cada palabra y buscar en Firebase
        palabras.forEachIndexed { index, palabra ->
            Log.d("BuscarTraducciones", "Procesando palabra: $palabra")

            // Normalizar la palabra antes de buscarla
            val palabraNormalizada = Normalizer.normalize(palabra, Normalizer.Form.NFD)
                .replace("[^\\p{ASCII}]".toRegex(), "")
            Log.d("BuscarTraducciones", "Palabra normalizada: $palabraNormalizada")

            firestore.collection("tu_coleccion")
                .whereEqualTo("palabra_espanol", palabraNormalizada) // Usar la palabra normalizada
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        Log.d("BuscarTraducciones", "No se encontró traducción para: $palabraNormalizada")
                    }

                    val traduccion = documents.mapNotNull { it.getString("palabra_cubeo") }
                        .firstOrNull() ?: palabra // Si no encuentra traducción, usar la palabra original

                    Log.d("BuscarTraducciones", "Traducción encontrada: $traduccion")
                    traduccionesList[index] = traduccion

                    // Actualizar solo si todas las palabras están traducidas
                    if (!traduccionesList.contains("")) {
                        _traducciones.postValue(traduccionesList)
                        Log.d("BuscarTraducciones", "Traducciones actualizadas: $traduccionesList")
                    }
                }
                .addOnFailureListener {
                    Log.d("BuscarTraducciones", "Error al consultar Firebase para la palabra: $palabraNormalizada")
                    traduccionesList[index] = palabra // Si hay un error, dejamos la palabra original

                    // Si todas las palabras han sido procesadas
                    if (!traduccionesList.contains("")) {
                        _traducciones.postValue(traduccionesList)
                        Log.d("BuscarTraducciones", "Traducciones finalizadas: $traduccionesList")
                    }
                }
        }
    }

    /**
     * Maneja errores al buscar traducciones.
     */
    private fun handleTranslationError(index: Int, palabra: String, traduccionesList: MutableList<String>) {
        traduccionesList[index] = palabra // Usa la palabra original si ocurre un error

        if (!traduccionesList.contains("")) {
            _traducciones.postValue(traduccionesList)
        }

        _error.postValue("Error al traducir la palabra: $palabra") // Opcional: Mostrar mensaje de error
    }
}