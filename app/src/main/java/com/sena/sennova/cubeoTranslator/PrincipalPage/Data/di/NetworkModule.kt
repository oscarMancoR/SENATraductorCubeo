package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.di

import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository.MBartApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // TODO: Cambiar esta URL cuando tengas el servidor desplegado
    private const val BASE_URL = "https://unseceded-hatlike-yaretzi.ngrok-free.dev/" // Para emulador Android
    // private const val BASE_URL = "https://tu-servidor.render.com/" // Para producción

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // mBART puede tardar
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideMBartApiService(retrofit: Retrofit): MBartApiService {
        return retrofit.create(MBartApiService::class.java)
    }
}