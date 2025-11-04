package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local


import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao.CacheTraduccionDao
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao.OracionDao
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao.PalabraDao
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao.SyncMetadataDao
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.CacheTraduccionApiEntity
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.OracionEntity
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.PalabraEntity
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDataSource @Inject constructor(
    private val palabraDao: PalabraDao,
    private val oracionDao: OracionDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val cacheTraduccionDao: CacheTraduccionDao
) {

    // ========== PALABRAS ==========

    suspend fun findPalabraByEspanol(palabra: String): PalabraEntity? {
        return palabraDao.findByEspanol(palabra.lowercase().trim())
    }

    suspend fun findPalabraByPamiwa(palabra: String): PalabraEntity? {
        return palabraDao.findByPamiwa(palabra.lowercase().trim())
    }

    suspend fun searchPalabrasEspanol(query: String): List<PalabraEntity> {
        return palabraDao.searchByEspanol(query.lowercase().trim())
    }

    suspend fun insertPalabras(palabras: List<PalabraEntity>) {
        palabraDao.insertAll(palabras)
    }

    suspend fun getPalabrasCount(): Int = palabraDao.getCount()

    fun getPalabrasCountFlow(): Flow<Int> = palabraDao.getCountFlow()

    // ========== ORACIONES ==========

    suspend fun findOracionExactaEspanol(oracion: String): OracionEntity? {
        return oracionDao.findExactByEspanol(oracion.lowercase().trim())
    }

    suspend fun findOracionExactaPamiwa(oracion: String): OracionEntity? {
        return oracionDao.findExactByPamiwa(oracion.lowercase().trim())
    }

    suspend fun searchOracionesByKeyword(keyword: String): List<OracionEntity> {
        return oracionDao.searchByKeywordEspanol(keyword.lowercase().trim())
    }

    suspend fun getAllOraciones(): List<OracionEntity> {
        return oracionDao.getAllOraciones()
    }

    suspend fun insertOraciones(oraciones: List<OracionEntity>) {
        oracionDao.insertAll(oraciones)
    }

    suspend fun getOracionesCount(): Int = oracionDao.getCount()

    fun getOracionesCountFlow(): Flow<Int> = oracionDao.getCountFlow()

    // ========== SYNC METADATA ==========

    suspend fun getSyncMetadata(collectionName: String): SyncMetadataEntity? {
        return syncMetadataDao.getMetadata(collectionName)
    }

    suspend fun updateSyncMetadata(metadata: SyncMetadataEntity) {
        syncMetadataDao.insertOrUpdate(metadata)
    }

    fun getAllSyncMetadataFlow(): Flow<List<SyncMetadataEntity>> {
        return syncMetadataDao.getAllMetadataFlow()
    }

    // ========== UTILIDADES ==========

    suspend fun clearAllData() {
        palabraDao.deleteAll()
        oracionDao.deleteAll()
    }

    // NUEVOS MÉTODOS para caché de API
    suspend fun buscarEnCacheApi(
        texto: String,
        direccion: String
    ): CacheTraduccionApiEntity? {
        return cacheTraduccionDao.buscarEnCache(
            texto.lowercase().trim(),
            direccion
        )
    }

    suspend fun guardarEnCacheApi(cache: CacheTraduccionApiEntity): Long {
        return cacheTraduccionDao.insertarCache(cache)
    }

    suspend fun limpiarCacheExpirado(): Int {
        return cacheTraduccionDao.limpiarCacheExpirado()
    }



}