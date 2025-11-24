package com.sena.sennova.cubeoTranslator.PrincipalPage.Data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.SyncMetadataEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val localDataSource: LocalDataSource,
    @ApplicationContext private val context: Context
) {

    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "SyncManager"

        //  NOMBRES ACTUALIZADOS DE COLECCIONES FIREBASE
        private const val COLLECTION_PALABRAS = "palabras"
        private const val COLLECTION_ORACIONES = "oraciones_completas"

        private const val BATCH_SIZE = 100
    }
    // ============================================================================
// VARIABLES DE CLASE EN SyncManager
// ============================================================================
    private var palabrasListener: ListenerRegistration? = null
    private var oracionesListener: ListenerRegistration? = null

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
            Log.d(TAG, "✅ Sincronización completada: $palabrasSynced palabras, $oracionesSynced oraciones")

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
     * Sincroniza solo palabras NUEVAS desde última sincronización
     */
    private suspend fun syncPalabras(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.InProgress(25, 100, "Descargando palabras...")

            // 🔴 OBTENER ÚLTIMA FECHA DE SINCRONIZACIÓN
            val metadata = localDataSource.getSyncMetadata("palabras")
            val ultimaSync = metadata?.last_sync_timestamp ?: 0L

            Log.d(TAG, "📥 Descargando palabras DESPUÉS de: ${java.util.Date(ultimaSync)}")

            // 🔴 SOLO PALABRAS NUEVAS O MODIFICADAS
            val snapshot = firestore.collection(COLLECTION_PALABRAS)
                .whereEqualTo("activo", true)
                .whereGreaterThan("created_at", ultimaSync)  // 🔴 FILTRO INCREMENTAL
                .get()
                .await()

            val totalPalabras = snapshot.documents.size
            Log.d(TAG, "📊 Palabras NUEVAS: $totalPalabras")

            if (totalPalabras == 0) {
                Log.d(TAG, "✅ No hay palabras nuevas")
                return@withContext Result.success(0)
            }

            _syncState.value = SyncState.InProgress(40, 100, "Procesando palabras...")

            val palabrasEntities = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data?.toPalabraEntity(doc.id)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error procesando palabra ${doc.id}: ${e.message}")
                    null
                }
            }

            _syncState.value = SyncState.InProgress(50, 100, "Guardando palabras...")

            // 🔴 NO LIMPIAR - Solo insertar/actualizar
            palabrasEntities.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
                localDataSource.insertPalabras(batch)
                val progress = 50 + ((index + 1) * 25 / (palabrasEntities.size / BATCH_SIZE + 1))
                _syncState.value = SyncState.InProgress(
                    progress,
                    100,
                    "Guardando palabras ${(index + 1) * BATCH_SIZE}/${palabrasEntities.size}"
                )
            }

            // Actualizar metadata
            localDataSource.updateSyncMetadata(
                SyncMetadataEntity(
                    collection_name = "palabras",
                    last_sync_timestamp = System.currentTimeMillis(),
                    total_records = (metadata?.total_records ?: 0) + palabrasEntities.size,
                    sync_status = "completed"
                )
            )

            Log.d(TAG, "✅ Sincronización incremental: ${palabrasEntities.size} palabras nuevas")
            Result.success(palabrasEntities.size)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sincronizando palabras", e)
            Result.failure(e)
        }
    }
    /**
     * Sincroniza solo oraciones NUEVAS desde última sincronización
     */
    private suspend fun syncOraciones(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.InProgress(75, 100, "Descargando oraciones...")

            // 🔴 OBTENER ÚLTIMA FECHA DE SINCRONIZACIÓN
            val metadata = localDataSource.getSyncMetadata("oraciones")
            val ultimaSync = metadata?.last_sync_timestamp ?: 0L

            Log.d(TAG, "📥 Descargando oraciones DESPUÉS de: ${java.util.Date(ultimaSync)}")

            // 🔴 SOLO ORACIONES NUEVAS O MODIFICADAS
            val snapshot = firestore.collection(COLLECTION_ORACIONES)
                .whereEqualTo("activo", true)
                .whereGreaterThan("created_at", ultimaSync)  // 🔴 FILTRO INCREMENTAL
                .get()
                .await()

            val totalOraciones = snapshot.documents.size
            Log.d(TAG, "📊 Oraciones NUEVAS: $totalOraciones")

            if (totalOraciones == 0) {
                Log.d(TAG, "✅ No hay oraciones nuevas")
                return@withContext Result.success(0)
            }

            _syncState.value = SyncState.InProgress(85, 100, "Procesando oraciones...")

            val oracionesEntities = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data?.toOracionEntity(doc.id)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error procesando oración ${doc.id}: ${e.message}")
                    null
                }
            }

            _syncState.value = SyncState.InProgress(90, 100, "Guardando oraciones...")

            // 🔴 NO LIMPIAR - Solo insertar/actualizar
            oracionesEntities.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
                localDataSource.insertOraciones(batch)
                val progress = 90 + ((index + 1) * 10 / (oracionesEntities.size / BATCH_SIZE + 1))
                _syncState.value = SyncState.InProgress(
                    progress,
                    100,
                    "Guardando oraciones ${(index + 1) * BATCH_SIZE}/${oracionesEntities.size}"
                )
            }

            // Actualizar metadata
            localDataSource.updateSyncMetadata(
                SyncMetadataEntity(
                    collection_name = "oraciones",
                    last_sync_timestamp = System.currentTimeMillis(),
                    total_records = (metadata?.total_records ?: 0) + oracionesEntities.size,
                    sync_status = "completed"
                )
            )

            Log.d(TAG, "✅ Sincronización incremental: ${oracionesEntities.size} oraciones nuevas")
            Result.success(oracionesEntities.size)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sincronizando oraciones", e)
            Result.failure(e)
        }
    }

    /**
     * Verifica si los datos están desactualizados (> 7 días)
     */
    private fun isDataOutdated(metadata: SyncMetadataEntity?): Boolean {
        if (metadata == null) return true

        val sevenDaysInMillis = 6 * 60 * 60 * 1000L
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
            Log.d(TAG, "🔄 Forzando resincronización completa...")
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

    /**
     * Inicia listeners en tiempo real para detectar cambios INMEDIATAMENTE
     * Se ejecuta cuando la app está abierta
     */
    fun startRealtimeSync() {
        Log.d(TAG, "🔄 Iniciando sincronización en tiempo real...")

        // Detener listeners anteriores si existen
        stopRealtimeSync()

        // Iniciar listener de palabras
        startPalabrasListener()

        // Iniciar listener de oraciones
        startOracionesListener()
    }

    /**
     * Detiene los listeners (cuando se cierra la app)
     */
    fun stopRealtimeSync() {
        palabrasListener?.remove()
        oracionesListener?.remove()
        Log.d(TAG, "🛑 Sincronización en tiempo real detenida")
    }

    // ============================================================================
// LISTENER DE PALABRAS
// ============================================================================
    private fun startPalabrasListener() {
        syncScope.launch {
            try {
                val metadata = localDataSource.getSyncMetadata("palabras")
                val ultimaSync = metadata?.last_sync_timestamp ?: 0L

                Log.d(TAG, "👂 Escuchando palabras DESPUÉS de: ${java.util.Date(ultimaSync)}")

                // 🔥 LISTENER EN TIEMPO REAL
                palabrasListener = firestore.collection(COLLECTION_PALABRAS)
                    .whereEqualTo("activo", true)
                    .whereGreaterThan("created_at", ultimaSync)
                    .addSnapshotListener { snapshot, error ->

                        if (error != null) {
                            Log.e(TAG, "❌ Error en listener de palabras", error)
                            return@addSnapshotListener
                        }

                        if (snapshot == null || snapshot.isEmpty) {
                            Log.d(TAG, "✅ No hay palabras nuevas")
                            return@addSnapshotListener
                        }

                        // Solo procesar documentos MODIFICADOS o AGREGADOS
                        val cambios = snapshot.documentChanges.filter { change ->
                            change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                                    change.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED
                        }

                        if (cambios.isEmpty()) {
                            return@addSnapshotListener
                        }

                        Log.d(TAG, "🔥 ${cambios.size} palabras nuevas/modificadas detectadas")

                        // Procesar cambios en background
                        syncScope.launch(Dispatchers.IO) {
                            try {
                                val palabrasNuevas = cambios.mapNotNull { change ->
                                    try {
                                        change.document.data.toPalabraEntity(change.document.id)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Error procesando palabra", e)
                                        null
                                    }
                                }

                                if (palabrasNuevas.isNotEmpty()) {
                                    localDataSource.insertPalabras(palabrasNuevas)

                                    // Actualizar metadata
                                    localDataSource.updateSyncMetadata(
                                        SyncMetadataEntity(
                                            collection_name = "palabras",
                                            last_sync_timestamp = System.currentTimeMillis(),
                                            total_records = (metadata?.total_records ?: 0) + palabrasNuevas.size,
                                            sync_status = "realtime_updated"
                                        )
                                    )

                                    Log.d(TAG, "✅ ${palabrasNuevas.size} palabras sincronizadas en tiempo real")
                                }

                            } catch (e: Exception) {
                                Log.e(TAG, "Error guardando palabras", e)
                            }
                        }
                    }

            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando listener de palabras", e)
            }
        }
    }

    // ============================================================================
// LISTENER DE ORACIONES
// ============================================================================
    private fun startOracionesListener() {
        syncScope.launch {
            try {
                val metadata = localDataSource.getSyncMetadata("oraciones")
                val ultimaSync = metadata?.last_sync_timestamp ?: 0L

                Log.d(TAG, "👂 Escuchando oraciones DESPUÉS de: ${java.util.Date(ultimaSync)}")

                // 🔥 LISTENER EN TIEMPO REAL
                oracionesListener = firestore.collection(COLLECTION_ORACIONES)
                    .whereEqualTo("activo", true)
                    .whereGreaterThan("created_at", ultimaSync)
                    .addSnapshotListener { snapshot, error ->

                        if (error != null) {
                            Log.e(TAG, "❌ Error en listener de oraciones", error)
                            return@addSnapshotListener
                        }

                        if (snapshot == null || snapshot.isEmpty) {
                            Log.d(TAG, "✅ No hay oraciones nuevas")
                            return@addSnapshotListener
                        }

                        // Solo procesar cambios reales
                        val cambios = snapshot.documentChanges.filter { change ->
                            change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                                    change.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED
                        }

                        if (cambios.isEmpty()) {
                            return@addSnapshotListener
                        }

                        Log.d(TAG, "🔥 ${cambios.size} oraciones nuevas/modificadas detectadas")

                        // Procesar cambios en background
                        syncScope.launch(Dispatchers.IO) {
                            try {
                                val oracionesNuevas = cambios.mapNotNull { change ->
                                    try {
                                        change.document.data.toOracionEntity(change.document.id)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Error procesando oración", e)
                                        null
                                    }
                                }

                                if (oracionesNuevas.isNotEmpty()) {
                                    localDataSource.insertOraciones(oracionesNuevas)

                                    // Actualizar metadata
                                    localDataSource.updateSyncMetadata(
                                        SyncMetadataEntity(
                                            collection_name = "oraciones",
                                            last_sync_timestamp = System.currentTimeMillis(),
                                            total_records = (metadata?.total_records ?: 0) + oracionesNuevas.size,
                                            sync_status = "realtime_updated"
                                        )
                                    )

                                    Log.d(TAG, "✅ ${oracionesNuevas.size} oraciones sincronizadas en tiempo real")
                                }

                            } catch (e: Exception) {
                                Log.e(TAG, "Error guardando oraciones", e)
                            }
                        }
                    }

            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando listener de oraciones", e)
            }
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

