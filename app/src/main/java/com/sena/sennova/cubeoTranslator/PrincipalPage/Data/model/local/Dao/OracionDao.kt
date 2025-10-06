package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.OracionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OracionDao {

    @Query("SELECT * FROM oraciones WHERE oracion_espanol = :oracion LIMIT 1")
    suspend fun findExactByEspanol(oracion: String): OracionEntity?

    @Query("SELECT * FROM oraciones WHERE oracion_pamiwa = :oracion LIMIT 1")
    suspend fun findExactByPamiwa(oracion: String): OracionEntity?

    @Query("SELECT * FROM oraciones WHERE oracion_espanol LIKE '%' || :keyword || '%' LIMIT 20")
    suspend fun searchByKeywordEspanol(keyword: String): List<OracionEntity>

    @Query("SELECT * FROM oraciones WHERE oracion_pamiwa LIKE '%' || :keyword || '%' LIMIT 20")
    suspend fun searchByKeywordPamiwa(keyword: String): List<OracionEntity>

    @Query("SELECT * FROM oraciones WHERE categoria = :categoria LIMIT 50")
    suspend fun findByCategoria(categoria: String): List<OracionEntity>

    @Query("SELECT * FROM oraciones")
    suspend fun getAllOraciones(): List<OracionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(oraciones: List<OracionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(oracion: OracionEntity)

    @Query("DELETE FROM oraciones")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM oraciones")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM oraciones")
    fun getCountFlow(): Flow<Int>
}
