package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface MBartApiService {

    @POST("traducir")
    suspend fun traducir(
        @Header("ngrok-skip-browser-warning") skipWarning: String = "true",
        @Body request: TraduccionApiRequest
    ): Response<TraduccionApiResponse>

    @GET("health")
    suspend fun verificarSalud(
        @Header("ngrok-skip-browser-warning") skipWarning: String = "true"
    ): Response<HealthResponse>
}

// DTO simplificado para tu API Nekamui
data class TraduccionApiRequest(
    val texto: String
)

data class TraduccionApiResponse(
    val exito: Boolean,
    val original: String,
    val traduccion: String,
    val error: String? = null
)

data class HealthResponse(
    val status: String,
    val modelo_cargado: Boolean
)

/*
interface MBartApiService {

    @POST("traducir")
    suspend fun traducir(
        @Body request: TraduccionApiRequest
    ): Response<TraduccionApiResponse>

    @POST("corregir")
    suspend fun enviarCorreccion(
        @Body correccion: CorreccionApiRequest
    ): Response<Unit>

    @GET("health")
    suspend fun verificarSalud(): Response<HealthResponse>
}

// Modelos para la API
data class TraduccionApiRequest(
    val texto: String,
    val idioma_origen: String, // "es" o "pam"
    val idioma_destino: String // "pam" o "es"
)

data class TraduccionApiResponse(
    val texto_original: String,
    val traduccion: String,
    val confianza: Float? = 0.75f,
    val tiempo_ms: Long? = 0
)

data class CorreccionApiRequest(
    val texto_original: String,
    val traduccion_modelo: String,
    val traduccion_correcta: String,
    val idioma_origen: String,
    val usuario_id: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class HealthResponse(
    val status: String,
    val modelo_cargado: Boolean,
    val version: String
)*/