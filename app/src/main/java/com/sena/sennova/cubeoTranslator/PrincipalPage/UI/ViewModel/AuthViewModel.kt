package com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.UsuarioTraductor
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUIState(
    val estaLogueado: Boolean = false,
    val usuario: UsuarioTraductor? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val mostrarDialogLogin: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUIState())
    val uiState: StateFlow<AuthUIState> = _uiState.asStateFlow()

    init {
        // Observar estado de auth
        viewModelScope.launch {
            authRepository.estaLogueado.collect { logueado ->
                _uiState.value = _uiState.value.copy(
                    estaLogueado = logueado,
                    usuario = authRepository.usuarioActual.value
                )
            }
        }
    }

    fun mostrarDialogLogin() {
        _uiState.value = _uiState.value.copy(mostrarDialogLogin = true)
    }

    fun ocultarDialogLogin() {
        _uiState.value = _uiState.value.copy(mostrarDialogLogin = false, error = null)
    }

    fun login(codigo: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null)

            val resultado = authRepository.loginConCodigo(codigo)

            resultado.fold(
                onSuccess = { usuario ->
                    _uiState.value = _uiState.value.copy(
                        cargando = false,
                        estaLogueado = true,
                        usuario = usuario,
                        mostrarDialogLogin = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        cargando = false,
                        error = error.message ?: "Error de login"
                    )
                }
            )
        }
    }

    fun logout() {
        authRepository.logout()
        _uiState.value = _uiState.value.copy(
            estaLogueado = false,
            usuario = null
        )
    }

    fun puedeEditar(): Boolean = authRepository.puedeEditar()

    suspend fun guardarCorreccion(
        textoOriginal: String,
        traduccionIA: String,
        traduccionCorrecta: String,
        direccion: String,
        notas: String = ""
    ): Result<Boolean> {
        return authRepository.guardarCorreccion(
            textoOriginal,
            traduccionIA,
            traduccionCorrecta,
            direccion,
            notas
        )
    }
}