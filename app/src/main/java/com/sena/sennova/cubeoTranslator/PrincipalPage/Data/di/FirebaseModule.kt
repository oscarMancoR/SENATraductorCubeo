package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.TranslationRepository
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.implementar.TranslationRepositoryImpl
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.util.SimilarityCalculator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseApp(@ApplicationContext context: Context): FirebaseApp {
        // Inicializar Firebase si no está inicializado
        return if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context) ?: throw IllegalStateException("Firebase initialization failed")
        } else {
            FirebaseApp.getInstance()
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(firebaseApp: FirebaseApp): FirebaseFirestore {
        return FirebaseFirestore.getInstance(firebaseApp)
    }
    @Provides
    @Singleton
    fun provideSimilarityCalculator(): SimilarityCalculator {
        return SimilarityCalculator()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTranslationRepository(
        translationRepositoryImpl: TranslationRepositoryImpl
    ): TranslationRepository
}