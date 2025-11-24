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

    suspend fun findPalabraByPamie(palabra: String): PalabraEntity? {
        return palabraDao.findByPamie(palabra.lowercase().trim())
    }

    suspend fun searchPalabrasEspanol(query: String): List<PalabraEntity> {
        return palabraDao.searchByEspanol(query.lowercase().trim())
    }

    suspend fun searchPalabrasPamie(query: String): List<PalabraEntity> {
        return palabraDao.searchByPamie(query.lowercase().trim())
    }

    suspend fun insertPalabras(palabras: List<PalabraEntity>) {
        palabraDao.insertAll(palabras)
    }

    suspend fun clearPalabras() {
        palabraDao.deleteAll()
    }

    suspend fun getPalabrasCount(): Int = palabraDao.getCount()

    fun getPalabrasCountFlow(): Flow<Int> = palabraDao.getCountFlow()

    suspend fun getAllPalabrasActivas(): List<PalabraEntity> {
        return palabraDao.getAllActive()
    }

    // ========== ORACIONES ==========

    suspend fun findOracionExactaEspanol(oracion: String): OracionEntity? {
        return oracionDao.findExactByEspanol(oracion.trim())
    }

    suspend fun findOracionExactaPamie(oracion: String): OracionEntity? {
        return oracionDao.findExactByPamie(oracion.trim())
    }

    // Busca en todas las variaciones temporales (presente, pasado, futuro)
    suspend fun findOracionByEspanolAllTiempos(oracion: String): OracionEntity? {
        return oracionDao.findExactByEspanolAllTiempos(oracion.trim())
    }

    suspend fun findOracionByPamieAllTiempos(oracion: String): OracionEntity? {
        return oracionDao.findExactByPamieAllTiempos(oracion.trim())
    }

    suspend fun searchOracionesByKeywordEspanol(keyword: String): List<OracionEntity> {
        return oracionDao.searchByKeywordEspanol(keyword.trim())
    }

    suspend fun searchOracionesByKeywordPamie(keyword: String): List<OracionEntity> {
        return oracionDao.searchByKeywordPamie(keyword.trim())
    }

    suspend fun findOracionesByFamilia(familia: String): List<OracionEntity> {
        return oracionDao.findByFamilia(familia)
    }

    suspend fun getAllFamilias(): List<String> {
        return oracionDao.getAllFamilias()
    }

    suspend fun getAllOraciones(): List<OracionEntity> {
        return oracionDao.getAllOraciones()
    }

    suspend fun insertOraciones(oraciones: List<OracionEntity>) {
        oracionDao.insertAll(oraciones)
    }

    suspend fun clearOraciones() {
        oracionDao.deleteAll()
    }

    suspend fun getOracionesCount(): Int = oracionDao.getCount()

    suspend fun getOracionesActiveCount(): Int = oracionDao.getActiveCount()

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

    // ========== CACHE API ==========

    suspend fun buscarEnCacheApi(
        texto: String,
        direccion: String
    ): CacheTraduccionApiEntity? {
        return cacheTraduccionDao.buscarEnCache(
            texto.lowercase().trim(),
            direccion
        )
    }

    suspend fun guardarCacheApi(cache: CacheTraduccionApiEntity): Long {
        return cacheTraduccionDao.insertarCache(cache)
    }

    suspend fun limpiarCacheExpirado(): Int {
        return cacheTraduccionDao.limpiarCacheExpirado()
    }

    suspend fun obtenerTamanoCache(): Int {
        return cacheTraduccionDao.obtenerTamanoCache()
    }

    suspend fun limpiarTodoCache(): Int {
        return cacheTraduccionDao.limpiarTodoCache()
    }

    // ========== UTILIDADES ==========

    suspend fun clearAllData() {
        palabraDao.deleteAll()
        oracionDao.deleteAll()
        cacheTraduccionDao.limpiarTodoCache()
    }

    // En la sección de PALABRAS
    suspend fun insertPalabra(palabra: PalabraEntity) {
        palabraDao.insert(palabra)
    }

    // En la sección de ORACIONES
    suspend fun insertOracion(oracion: OracionEntity) {
        oracionDao.insert(oracion)
    }


    suspend fun getPalabraPorEspanol(palabraEspanol: String): PalabraEntity? {
        return palabraDao.getPalabraPorEspanol(palabraEspanol)
    }

    suspend fun updatePalabra(palabra: PalabraEntity) {
        palabraDao.updatePalabra(palabra)
    }

    suspend fun getOracionPorEspanol(espanolPresente: String): OracionEntity? {
        return oracionDao.getOracionPorEspanol(espanolPresente)
    }

    suspend fun updateOracion(oracion: OracionEntity) {
        oracionDao.update(oracion)
    }
}