package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao.OracionDao
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao.PalabraDao
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao.SyncMetadataDao
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.OracionEntity
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.PalabraEntity
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.SyncMetadataEntity


@Database(
    entities = [
        PalabraEntity::class,
        OracionEntity::class,
        SyncMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class TranslationDatabase : RoomDatabase() {

    abstract fun palabraDao(): PalabraDao
    abstract fun oracionDao(): OracionDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        const val DATABASE_NAME = "translation_database"
    }
}
