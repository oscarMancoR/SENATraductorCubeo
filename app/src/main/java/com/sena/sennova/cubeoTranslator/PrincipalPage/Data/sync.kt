package com.sena.sennova.cubeoTranslator.PrincipalPage.Data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.SyncMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncState {
    object Idle : SyncState()
    data class InProgress(val progress: Int, val total: Int, val message: String) : SyncState()
    data class Success(val palabrasSynced: Int, val oracionesSynced: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

@Singleton
class SyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val localDataSource: LocalDataSource
) {

    companion object {
        private const val TAG = "SyncManager"
        private const val COLLECTION_PALABRAS = "tu_coleccion"
        private const val COLLECTION_ORACIONES = "oraciones_coleccion"
        private const val BATCH_SIZE = 100
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Sincronización completa: Descarga todo desde Firebase
     */
    suspend fun performFullSync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando sincronización completa...")
            _syncState.value = SyncState.InProgress(0, 100, "Iniciando sincronización...")

            // Sincronizar palabras
            val palabrasResult = syncPalabras()
            if (palabrasResult.isFailure) {
                val error = palabrasResult.exceptionOrNull()?.message ?: "Error desconocido"
                _syncState.value = SyncState.Error("Error sincronizando palabras: $error")
                return@withContext Result.failure(palabrasResult.exceptionOrNull()!!)
            }

            // Sincronizar oraciones
            val oracionesResult = syncOraciones()
            if (oracionesResult.isFailure) {
                val error = oracionesResult.exceptionOrNull()?.message ?: "Error desconocido"
                _syncState.value = SyncState.Error("Error sincronizando oraciones: $error")
                return@withContext Result.failure(oracionesResult.exceptionOrNull()!!)
            }

            val palabrasSynced = palabrasResult.getOrNull() ?: 0
            val oracionesSynced = oracionesResult.getOrNull() ?: 0

            _syncState.value = SyncState.Success(palabrasSynced, oracionesSynced)
            Log.d(TAG, "Sincronización completada: $palabrasSynced palabras, $oracionesSynced oraciones")

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error en sincronización completa", e)
            _syncState.value = SyncState.Error(e.message ?: "Error desconocido")
            Result.failure(e)
        }
    }

    /**
     * Sincroniza solo si es necesario (primera vez o desactualizado)
     */
    suspend fun syncIfNeeded(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val palabrasMetadata = localDataSource.getSyncMetadata("palabras")
            val oracionesMetadata = localDataSource.getSyncMetadata("oraciones")

            val needsSync = palabrasMetadata == null ||
                    oracionesMetadata == null ||
                    isDataOutdated(palabrasMetadata) ||
                    isDataOutdated(oracionesMetadata)

            if (needsSync) {
                Log.d(TAG, "Datos desactualizados, iniciando sincronización...")
                performFullSync()
            } else {
                Log.d(TAG, "Datos actualizados, sincronización no necesaria")
                _syncState.value = SyncState.Idle
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando necesidad de sync", e)
            Result.failure(e)
        }
    }

    /**
     * Sincroniza palabras desde Firebase
     */
    private suspend fun syncPalabras(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.InProgress(25, 100, "Descargando palabras...")

            Log.d(TAG, "Descargando palabras desde Firebase...")
            val snapshot = firestore.collection(COLLECTION_PALABRAS)
                .get()
                .await()

            val totalPalabras = snapshot.documents.size
            Log.d(TAG, "Obtenidas $totalPalabras palabras desde Firebase")

            _syncState.value = SyncState.InProgress(40, 100, "Procesando palabras...")

            val palabrasEntities = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data?.toPalabraEntity(doc.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error procesando palabra ${doc.id}: ${e.message}")
                    null
                }
            }

            _syncState.value = SyncState.InProgress(50, 100, "Guardando palabras...")

            // Insertar en batches para mejor rendimiento
            palabrasEntities.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
                localDataSource.insertPalabras(batch)
                val progress = 50 + ((index + 1) * 25 / (palabrasEntities.size / BATCH_SIZE + 1))
                _syncState.value = SyncState.InProgress(
                    progress,
                    100,
                    "Guardando palabras ${index * BATCH_SIZE}/${palabrasEntities.size}"
                )
            }

            // Actualizar metadata
            localDataSource.updateSyncMetadata(
                SyncMetadataEntity(
                    collection_name = "palabras",
                    last_sync_timestamp = System.currentTimeMillis(),
                    total_records = palabrasEntities.size,
                    sync_status = "completed"
                )
            )

            Log.d(TAG, "Sincronización de palabras completada: ${palabrasEntities.size}")
            Result.success(palabrasEntities.size)

        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando palabras", e)

            localDataSource.updateSyncMetadata(
                SyncMetadataEntity(
                    collection_name = "palabras",
                    last_sync_timestamp = System.currentTimeMillis(),
                    total_records = 0,
                    sync_status = "error"
                )
            )

            Result.failure(e)
        }
    }

    /**
     * Sincroniza oraciones desde Firebase
     */
    private suspend fun syncOraciones(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.InProgress(75, 100, "Descargando oraciones...")

            Log.d(TAG, "Descargando oraciones desde Firebase...")
            val snapshot = firestore.collection(COLLECTION_ORACIONES)
                .get()
                .await()

            val totalOraciones = snapshot.documents.size
            Log.d(TAG, "Obtenidas $totalOraciones oraciones desde Firebase")

            _syncState.value = SyncState.InProgress(85, 100, "Procesando oraciones...")

            val oracionesEntities = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data?.toOracionEntity(doc.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error procesando oración ${doc.id}: ${e.message}")
                    null
                }
            }

            _syncState.value = SyncState.InProgress(90, 100, "Guardando oraciones...")

            // Insertar en batches
            oracionesEntities.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
                localDataSource.insertOraciones(batch)
                val progress = 90 + ((index + 1) * 10 / (oracionesEntities.size / BATCH_SIZE + 1))
                _syncState.value = SyncState.InProgress(
                    progress,
                    100,
                    "Guardando oraciones ${index * BATCH_SIZE}/${oracionesEntities.size}"
                )
            }

            // Actualizar metadata
            localDataSource.updateSyncMetadata(
                SyncMetadataEntity(
                    collection_name = "oraciones",
                    last_sync_timestamp = System.currentTimeMillis(),
                    total_records = oracionesEntities.size,
                    sync_status = "completed"
                )
            )

            Log.d(TAG, "Sincronización de oraciones completada: ${oracionesEntities.size}")
            Result.success(oracionesEntities.size)

        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando oraciones", e)

            localDataSource.updateSyncMetadata(
                SyncMetadataEntity(
                    collection_name = "oraciones",
                    last_sync_timestamp = System.currentTimeMillis(),
                    total_records = 0,
                    sync_status = "error"
                )
            )

            Result.failure(e)
        }
    }

    /**
     * Verifica si los datos están desactualizados (> 7 días)
     */
    private fun isDataOutdated(metadata: SyncMetadataEntity?): Boolean {
        if (metadata == null) return true

        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L
        val timeSinceLastSync = System.currentTimeMillis() - metadata.last_sync_timestamp

        return timeSinceLastSync > sevenDaysInMillis
    }

    /**
     * Verifica si la base de datos local tiene datos
     */
    suspend fun hasLocalData(): Boolean {
        return try {
            val palabrasCount = localDataSource.getPalabrasCount()
            val oracionesCount = localDataSource.getOracionesCount()

            palabrasCount > 0 || oracionesCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando datos locales", e)
            false
        }
    }

    /**
     * Fuerza resincronización borrando datos locales primero
     */
    suspend fun forceResync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Forzando resincronización completa...")
            localDataSource.clearAllData()
            performFullSync()
        } catch (e: Exception) {
            Log.e(TAG, "Error en resincronización forzada", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene estadísticas de sincronización
     */
    suspend fun getSyncStats(): SyncStats {
        return try {
            val palabrasCount = localDataSource.getPalabrasCount()
            val oracionesCount = localDataSource.getOracionesCount()
            val palabrasMetadata = localDataSource.getSyncMetadata("palabras")
            val oracionesMetadata = localDataSource.getSyncMetadata("oraciones")

            SyncStats(
                totalPalabras = palabrasCount,
                totalOraciones = oracionesCount,
                lastSyncPalabras = palabrasMetadata?.last_sync_timestamp,
                lastSyncOraciones = oracionesMetadata?.last_sync_timestamp,
                syncStatusPalabras = palabrasMetadata?.sync_status ?: "never",
                syncStatusOraciones = oracionesMetadata?.sync_status ?: "never"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo stats de sync", e)
            SyncStats()
        }
    }
}

data class SyncStats(
    val totalPalabras: Int = 0,
    val totalOraciones: Int = 0,
    val lastSyncPalabras: Long? = null,
    val lastSyncOraciones: Long? = null,
    val syncStatusPalabras: String = "never",
    val syncStatusOraciones: String = "never"
)

// ============================================================================
// EXTENSIÓN PARA FORMATEAR TIEMPO
// ============================================================================

fun Long.toTimeAgo(): String {
    val now = System.currentTimeMillis()
    val diff = now - this

    return when {
        diff < 60_000 -> "Hace un momento"
        diff < 3_600_000 -> "Hace ${diff / 60_000} minutos"
        diff < 86_400_000 -> "Hace ${diff / 3_600_000} horas"
        diff < 604_800_000 -> "Hace ${diff / 86_400_000} días"
        else -> "Hace más de una semana"
    }
}