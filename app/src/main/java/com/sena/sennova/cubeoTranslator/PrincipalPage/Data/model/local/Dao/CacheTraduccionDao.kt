package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao

import androidx.room.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.CacheTraduccionApiEntity

@Dao
interface CacheTraduccionDao {

    @Query("""
        SELECT * FROM cache_api_traducciones 
        WHERE texto_original = :texto 
        AND direccion = :direccion 
        AND expira_en > :ahora
        LIMIT 1
    """)
    suspend fun buscarEnCache(
        texto: String,
        direccion: String,
        ahora: Long = System.currentTimeMillis()
    ): CacheTraduccionApiEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCache(cache: CacheTraduccionApiEntity): Long

    @Query("DELETE FROM cache_api_traducciones WHERE expira_en < :ahora")
    suspend fun limpiarCacheExpirado(ahora: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM cache_api_traducciones")
    suspend fun obtenerTamanoCache(): Int

    @Query("DELETE FROM cache_api_traducciones")
    suspend fun limpiarTodoCache(): Int

    @Query("""
        SELECT * FROM cache_api_traducciones 
        ORDER BY timestamp DESC 
        LIMIT :limite
    """)
    suspend fun obtenerRecientes(limite: Int = 20): List<CacheTraduccionApiEntity>
}