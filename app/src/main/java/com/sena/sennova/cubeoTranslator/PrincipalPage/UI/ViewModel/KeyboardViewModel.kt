package com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.Tecla
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class Idioma { ESPANOL, PAMIWA }

@HiltViewModel
class KeyboardViewModel @Inject constructor(): ViewModel() {

    // Modo actual (por defecto: ESPANOL -> PAMIWA)
    private val _idioma = MutableLiveData(Idioma.ESPANOL)
    val idioma: LiveData<Idioma> = _idioma

    // Texto actual (lo que se va escribiendo)
    private val _texto = MutableLiveData("")
    val texto: LiveData<String> = _texto

    // Si estamos mostrando la vista de símbolos
    private val _isSymbols = MutableLiveData(false)
    val isSymbols: LiveData<Boolean> = _isSymbols

    // Teclas visibles (actualiza cuando cambia idioma o modo símbolos)
    private val _teclas = MutableLiveData<List<Tecla>>(emptyList())
    val teclas: LiveData<List<Tecla>> = _teclas

    // Inicializar teclado
    init {
        cargarTeclado()
    }

    fun toggleIdioma() {
        _idioma.value = if (_idioma.value == Idioma.ESPANOL) Idioma.PAMIWA else Idioma.ESPANOL
        // al cambiar idioma, volver a la vista de letras (no símbolos)
        _isSymbols.value = false
        cargarTeclado()
    }

    fun toggleSymbols() {
        _isSymbols.value = _isSymbols.value != true
        cargarTeclado()
    }

    private fun cargarTeclado() {
        if (_isSymbols.value == true) {
            _teclas.value = generarTecladoSimbolos()
        } else {
            _teclas.value = when (_idioma.value) {
                Idioma.PAMIWA -> generarTecladoPamiwa()
                else -> generarTecladoEspanol()
            }
        }
    }

    // Manejo de pulsación de tecla
    fun onKeyPress(tecla: Tecla) {
        when (tecla.value) {
            "BACKSPACE" -> {
                _texto.value = _texto.value?.let {
                    if (it.isNotEmpty()) it.dropLast(1) else it
                }
            }
            "SPACE" -> {
                _texto.value = _texto.value?.plus(" ")
            }
            "SWITCH_SYMBOLS" -> {
                toggleSymbols()
            }
            else -> {
                // Inserta el valor de la tecla
                _texto.value = _texto.value?.plus(tecla.value)
            }
        }
    }

    fun setText(value: String) {
        _texto.value = value
    }

    fun clearText() {
        _texto.value = ""
    }

    // --- Generadores de teclados ---
    private fun generarTecladoEspanol(): List<Tecla> {
        // Distribución típica, filas; aquí flatten para LiveData
        val rows = listOf(
            listOf("q","w","e","r","t","y","u","i","o","p"),
            listOf("a","s","d","f","g","h","j","k","l","ñ"),
            listOf("z","x","c","v","b","n","m"),
            listOf("á","é","í","ó","ú","ü")
        )
        val keys = mutableListOf<Tecla>()
        rows.forEach { row -> row.forEach { keys.add(Tecla(it.uppercase(), it)) } }
        // fila especial con símbolos, espacio y backspace
        keys.add(Tecla("?123","SWITCH_SYMBOLS"))
        keys.add(Tecla("SPACE","SPACE"))
        keys.add(Tecla("⌫","BACKSPACE"))
        return keys
    }

    private fun generarTecladoPamiwa(): List<Tecla> {
        // Hecho a partir del análisis del corpus: letras básicas + vocales/diacríticos + caracteres especiales
        val row1 = listOf("q","w","e","r","t","y","u","i","o","p")
        val row2 = listOf("a","s","d","f","g","h","j","k","l","ñ")
        val row3 = listOf("z","x","c","v","b","n","m","ɨ","ᵾ") // ɨ y ᵾ incluidos
        val row4 = listOf("á","à","â","ã","ā","ẽ","ũ","õ","ó")  // vocales con variantes comunes en tu corpus
        val row5 = listOf("ù","ú","û","î","ì","í","ð","ñ") // otras variantes detectadas

        val keys = mutableListOf<Tecla>()
        (row1 + row2 + row3 + row4 + row5).forEach { k ->
            // Mostrar mayúscula en label, insertar el valor exacto
            keys.add(Tecla(k.uppercase(), k))
        }
        // fila especial: cambiar a símbolos, espacio, borrar
        keys.add(Tecla("?123","SWITCH_SYMBOLS"))
        keys.add(Tecla("SPACE","SPACE"))
        keys.add(Tecla("⌫","BACKSPACE"))
        return keys
    }

    private fun generarTecladoSimbolos(): List<Tecla> {
        val row1 = listOf("1","2","3","4","5","6","7","8","9","0")
        val row2 = listOf("!","?","¡","¿",",",".",";",":","~")
        val row3 = listOf("à","â","ê","î","ô","û","ã","õ","ñ")
        val row4 = listOf("(",")","[","]","{","}","/","-","_")

        val keys = mutableListOf<Tecla>()
        (row1 + row2 + row3 + row4).forEach { k -> keys.add(Tecla(k, k)) }
        keys.add(Tecla("ABC","SWITCH_SYMBOLS"))
        keys.add(Tecla("SPACE","SPACE"))
        keys.add(Tecla("⌫","BACKSPACE"))
        return keys
    }
}