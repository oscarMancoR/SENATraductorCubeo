package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.OracionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OracionDao {

    // =========================================================================
    // BÚSQUEDAS EXACTAS - PRESENTE (por defecto)
    // =========================================================================

    @Query("SELECT * FROM oraciones WHERE LOWER(espanol_presente) = LOWER(:oracion) AND activo = 1 LIMIT 1")
    suspend fun findExactByEspanol(oracion: String): OracionEntity?

    @Query("SELECT * FROM oraciones WHERE LOWER(pamie_presente) = LOWER(:oracion) AND activo = 1 LIMIT 1")
    suspend fun findExactByPamie(oracion: String): OracionEntity?

    // =========================================================================
    // BÚSQUEDAS EN TODAS LAS VARIACIONES TEMPORALES
    // =========================================================================

    @Query("""
        SELECT * FROM oraciones 
        WHERE activo = 1 AND (
            LOWER(espanol_presente) = LOWER(:oracion) OR
            LOWER(espanol_pasado) = LOWER(:oracion) OR
            LOWER(espanol_futuro) = LOWER(:oracion)
        )
        LIMIT 1
    """)
    suspend fun findExactByEspanolAllTiempos(oracion: String): OracionEntity?

    @Query("""
        SELECT * FROM oraciones 
        WHERE activo = 1 AND (
            LOWER(pamie_presente) = LOWER(:oracion) OR
            LOWER(pamie_pasado) = LOWER(:oracion) OR
            LOWER(pamie_futuro) = LOWER(:oracion)
        )
        LIMIT 1
    """)
    suspend fun findExactByPamieAllTiempos(oracion: String): OracionEntity?

    // =========================================================================
    // BÚSQUEDAS POR KEYWORD
    // =========================================================================

    @Query("""
        SELECT * FROM oraciones 
        WHERE activo = 1 AND (
            espanol_presente LIKE '%' || :keyword || '%' OR
            espanol_pasado LIKE '%' || :keyword || '%' OR
            espanol_futuro LIKE '%' || :keyword || '%'
        )
        LIMIT 20
    """)
    suspend fun searchByKeywordEspanol(keyword: String): List<OracionEntity>

    @Query("""
        SELECT * FROM oraciones 
        WHERE activo = 1 AND (
            pamie_presente LIKE '%' || :keyword || '%' OR
            pamie_pasado LIKE '%' || :keyword || '%' OR
            pamie_futuro LIKE '%' || :keyword || '%'
        )
        LIMIT 20
    """)
    suspend fun searchByKeywordPamie(keyword: String): List<OracionEntity>

    // =========================================================================
    // BÚSQUEDAS POR FAMILIA
    // =========================================================================

    @Query("SELECT * FROM oraciones WHERE familia = :familia AND activo = 1 LIMIT 50")
    suspend fun findByFamilia(familia: String): List<OracionEntity>

    @Query("SELECT DISTINCT familia FROM oraciones WHERE activo = 1")
    suspend fun getAllFamilias(): List<String>

    // =========================================================================
    // OBTENER TODAS
    // =========================================================================

    @Query("SELECT * FROM oraciones WHERE activo = 1")
    suspend fun getAllOraciones(): List<OracionEntity>

    @Query("SELECT * FROM oraciones")
    suspend fun getAllIncludingInactive(): List<OracionEntity>

    // =========================================================================
    // INSERTS
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(oraciones: List<OracionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(oracion: OracionEntity)

    // =========================================================================
    // DELETES Y COUNTS
    // =========================================================================

    @Query("DELETE FROM oraciones")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM oraciones")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM oraciones WHERE activo = 1")
    suspend fun getActiveCount(): Int

    @Query("SELECT COUNT(*) FROM oraciones")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT * FROM oraciones WHERE espanol_presente = :espanolPresente LIMIT 1")
    suspend fun getOracionPorEspanol(espanolPresente: String): OracionEntity?

    @Update
    suspend fun update(oracion: OracionEntity)

}