package com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class InitializationState {
    object Idle : InitializationState()
    data class Syncing(val progress: Int, val message: String) : InitializationState()
    object Ready : InitializationState()
    data class Error(val message: String) : InitializationState()
}

@HiltViewModel
class InitializationViewModel @Inject constructor(
    private val syncManager: SyncManager
) : ViewModel() {

    private val _initializationState = MutableStateFlow<InitializationState>(InitializationState.Idle)
    val initializationState: StateFlow<InitializationState> = _initializationState.asStateFlow()

    init {
        observeSyncState()
        checkAndInitialize()
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.syncState.collect { syncState ->
                _initializationState.value = when (syncState) {
                    is SyncState.Idle -> InitializationState.Idle

                    is SyncState.InProgress -> InitializationState.Syncing(
                        progress = syncState.progress,
                        message = syncState.message
                    )

                    is SyncState.Success -> {
                        InitializationState.Ready
                    }

                    is SyncState.Error -> InitializationState.Error(syncState.message)
                }
            }
        }
    }

    private fun checkAndInitialize() {
        viewModelScope.launch {
            try {
                val hasData = syncManager.hasLocalData()

                if (hasData) {
                    // Ya hay datos, marcar como listo
                    _initializationState.value = InitializationState.Ready

                    // Sincronizar en background si es necesario
                    syncManager.syncIfNeeded()
                } else {
                    // No hay datos, necesita sincronización inicial
                    _initializationState.value = InitializationState.Syncing(0, "Preparando...")
                    val result = syncManager.performFullSync()

                    if (result.isFailure) {
                        _initializationState.value = InitializationState.Error(
                            result.exceptionOrNull()?.message ?: "Error desconocido"
                        )
                    }
                }
            } catch (e: Exception) {
                _initializationState.value = InitializationState.Error(e.message ?: "Error al inicializar")
            }
        }
    }

    fun retrySync() {
        viewModelScope.launch {
            _initializationState.value = InitializationState.Syncing(0, "Reintentando...")
            val result = syncManager.performFullSync()

            if (result.isFailure) {
                _initializationState.value = InitializationState.Error(
                    result.exceptionOrNull()?.message ?: "Error en sincronización"
                )
            }
        }
    }

    fun forceResync() {
        viewModelScope.launch {
            _initializationState.value = InitializationState.Syncing(0, "Descargando datos...")
            val result = syncManager.forceResync()

            if (result.isFailure) {
                _initializationState.value = InitializationState.Error(
                    result.exceptionOrNull()?.message ?: "Error en resincronización"
                )
            }
        }
    }
}