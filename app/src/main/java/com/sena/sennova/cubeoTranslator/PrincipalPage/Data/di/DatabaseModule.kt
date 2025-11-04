package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.di

import android.content.Context
import androidx.room.Room
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao.CacheTraduccionDao
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao.OracionDao
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao.PalabraDao
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.Dao.SyncMetadataDao
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.TranslationDatabase

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCacheTraduccionDao(database: TranslationDatabase): CacheTraduccionDao {
        return database.cacheTraduccionDao()
    }

    @Provides
    @Singleton
    fun provideTranslationDatabase(
        @ApplicationContext context: Context
    ): TranslationDatabase {
        return Room.databaseBuilder(
            context,
            TranslationDatabase::class.java,
            TranslationDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // Solo para desarrollo
            .build()
    }

    @Provides
    @Singleton
    fun providePalabraDao(database: TranslationDatabase): PalabraDao {
        return database.palabraDao()
    }

    @Provides
    @Singleton
    fun provideOracionDao(database: TranslationDatabase): OracionDao {
        return database.oracionDao()
    }

    @Provides
    @Singleton
    fun provideSyncMetadataDao(database: TranslationDatabase): SyncMetadataDao {
        return database.syncMetadataDao()
    }


}
