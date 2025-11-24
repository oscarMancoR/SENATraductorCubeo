package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model

data class UsuarioTraductor(
    val id: String = "",
    val nombre: String = "",
    val email: String = "",
    val codigoAcceso: String = "",
    val activo: Boolean = true,
    val rol: String = "traductor",
    val correccionesRealizadas: Int = 0,
    val fechaRegistro: Long = 0,
    val ultimoAcceso: Long = 0
)

data class CorreccionValidada(
    val id: String = "",
    val textoOriginal: String = "",
    val traduccionIA: String = "",
    val traduccionCorrecta: String = "",
    val direccion: String = "",
    val usuarioId: String = "",
    val usuarioNombre: String = "",
    val fechaCorreccion: Long = System.currentTimeMillis(),
    val aplicadaACorpus: Boolean = false,
    val notas: String = ""
)
