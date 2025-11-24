package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao

import androidx.room.*
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.PalabraEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PalabraDao {

    @Query("SELECT * FROM palabras WHERE LOWER(palabra_espanol) = LOWER(:palabra) LIMIT 1")
    suspend fun findByEspanol(palabra: String): PalabraEntity?

    @Query("SELECT * FROM palabras WHERE LOWER(palabra_pamie) = LOWER(:palabra) LIMIT 1")
    suspend fun findByPamie(palabra: String): PalabraEntity?

    @Query("SELECT * FROM palabras WHERE palabra_espanol LIKE '%' || :palabra || '%' LIMIT 10")
    suspend fun searchByEspanol(palabra: String): List<PalabraEntity>

    @Query("SELECT * FROM palabras WHERE palabra_pamie LIKE '%' || :palabra || '%' LIMIT 10")
    suspend fun searchByPamie(palabra: String): List<PalabraEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(palabras: List<PalabraEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(palabra: PalabraEntity)

    @Query("DELETE FROM palabras")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM palabras")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM palabras")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT * FROM palabras WHERE activo = 1")
    suspend fun getAllActive(): List<PalabraEntity>

    // En PalabraDao.kt
    @Query("SELECT * FROM palabras WHERE palabra_espanol = :palabraEspanol LIMIT 1")
    suspend fun getPalabraPorEspanol(palabraEspanol: String): PalabraEntity?

    @Update
    suspend fun updatePalabra(palabra: PalabraEntity)
}