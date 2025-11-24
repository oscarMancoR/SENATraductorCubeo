package com.sena.sennova.cubeoTranslator.PrincipalPage.Data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.CorreccionValidada
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.UsuarioTraductor
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.LocalDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.PalabraEntity
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.local.entity.OracionEntity

@Singleton
class AuthRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context,
    private val localDataSource: LocalDataSource
) {
    companion object {
        private const val TAG = "AuthRepository"
        private const val PREFS_NAME = "nekamui_auth"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ROL = "user_rol"
        private const val COLLECTION_USUARIOS = "usuarios_traductores"
        private const val COLLECTION_CORRECCIONES = "correcciones_validadas"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _usuarioActual = MutableStateFlow<UsuarioTraductor?>(null)
    val usuarioActual: StateFlow<UsuarioTraductor?> = _usuarioActual

    private val _estaLogueado = MutableStateFlow(false)
    val estaLogueado: StateFlow<Boolean> = _estaLogueado

    init {
        // Verificar si hay sesión guardada
        cargarSesionGuardada()
    }

    private fun cargarSesionGuardada() {
        val userId = prefs.getString(KEY_USER_ID, null)
        val userName = prefs.getString(KEY_USER_NAME, null)
        val userRol = prefs.getString(KEY_USER_ROL, null)

        if (userId != null && userName != null) {
            _usuarioActual.value = UsuarioTraductor(
                id = userId,
                nombre = userName,
                rol = userRol ?: "traductor"
            )
            _estaLogueado.value = true
            Log.d(TAG, "Sesión restaurada: $userName")
        }
    }

    /**
     * Login con código de acceso simple
     */
    suspend fun loginConCodigo(codigo: String): Result<UsuarioTraductor> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Intentando login con código...")

            val snapshot = firestore.collection(COLLECTION_USUARIOS)
                .whereEqualTo("codigo_acceso", codigo.uppercase().trim())
                .whereEqualTo("activo", true)
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "Código no encontrado o usuario inactivo")
                return@withContext Result.failure(Exception("Código de acceso inválido"))
            }

            val doc = snapshot.documents.first()
            val usuario = UsuarioTraductor(
                id = doc.id,
                nombre = doc.getString("nombre") ?: "",
                email = doc.getString("email") ?: "",
                codigoAcceso = codigo,
                activo = true,
                rol = doc.getString("rol") ?: "traductor",
                correccionesRealizadas = doc.getLong("correcciones_realizadas")?.toInt() ?: 0
            )

            // Guardar sesión
            prefs.edit()
                .putString(KEY_USER_ID, usuario.id)
                .putString(KEY_USER_NAME, usuario.nombre)
                .putString(KEY_USER_ROL, usuario.rol)
                .apply()

            // Actualizar último acceso
            firestore.collection(COLLECTION_USUARIOS)
                .document(doc.id)
                .update("ultimo_acceso", System.currentTimeMillis())

            _usuarioActual.value = usuario
            _estaLogueado.value = true

            Log.d(TAG, "✅ Login exitoso: ${usuario.nombre}")
            Result.success(usuario)

        } catch (e: Exception) {
            Log.e(TAG, "Error en login", e)
            Result.failure(e)
        }
    }

    /**
     * Cerrar sesión
     */
    fun logout() {
        prefs.edit().clear().apply()
        _usuarioActual.value = null
        _estaLogueado.value = false
        Log.d(TAG, "Sesión cerrada")
    }

    /**
     * Guardar corrección y agregarla directamente al corpus
     */
    suspend fun guardarCorreccion(
        textoOriginal: String,
        traduccionIA: String,
        traduccionCorrecta: String,
        direccion: String,
        notas: String = ""
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val usuario = _usuarioActual.value
                ?: return@withContext Result.failure(Exception("No logueado"))

            // 1. Guardar en correcciones_validadas (historial)
            val correccion = hashMapOf(
                "texto_original" to textoOriginal,
                "traduccion_ia" to traduccionIA,
                "traduccion_correcta" to traduccionCorrecta,
                "direccion" to direccion,
                "usuario_id" to usuario.id,
                "usuario_nombre" to usuario.nombre,
                "fecha_correccion" to System.currentTimeMillis(),
                "aplicada_a_corpus" to true,  // Ya se aplica directo
                "notas" to notas
            )

            firestore.collection(COLLECTION_CORRECCIONES)
                .add(correccion)
                .await()

            // 2. Determinar si es palabra u oración
            val esPalabra = textoOriginal.trim().split(" ").size == 1

            // 3. Guardar en el corpus correspondiente
            if (esPalabra) {
                guardarPalabraEnCorpus(textoOriginal, traduccionCorrecta, direccion, usuario.nombre)
                guardarPalabraEnCacheLocal(textoOriginal, traduccionCorrecta, direccion, usuario.nombre)
            } else {
                guardarOracionEnCorpus(textoOriginal, traduccionCorrecta, direccion, usuario.nombre)
                guardarOracionEnCacheLocal(textoOriginal, traduccionCorrecta, direccion, usuario.nombre)
            }

            // 4. Incrementar contador del usuario
            firestore.collection(COLLECTION_USUARIOS)
                .document(usuario.id)
                .update("correcciones_realizadas", usuario.correccionesRealizadas + 1)

            Log.d(TAG, "✅ Corrección guardada en corpus: $textoOriginal → $traduccionCorrecta")
            Result.success(true)

        } catch (e: Exception) {
            Log.e(TAG, "Error guardando corrección", e)
            Result.failure(e)
        }
    }



    /**
     * Guardar palabra nueva en Firebase
     */
    private suspend fun guardarPalabraEnCorpus(
        textoOriginal: String,
        traduccion: String,
        direccion: String,
        nombreUsuario: String
    ) {
        val esEspanolAPamiwa = direccion == "ES_TO_PAMIWA"

        val palabraData = hashMapOf(
            "palabra_espanol" to if (esEspanolAPamiwa) textoOriginal.lowercase().trim() else traduccion.lowercase().trim(),
            "palabra_pamie" to if (esEspanolAPamiwa) traduccion.trim() else textoOriginal.trim(),
            "significado" to "",
            "tipo_palabra" to "general",
            "activo" to true,
            "fuente" to "correccion_$nombreUsuario",
            "confianza" to 0.9f,
            "created_at" to System.currentTimeMillis()
        )

        // Buscar si ya existe la palabra
        val existente = firestore.collection("palabras")
            .whereEqualTo("palabra_espanol", palabraData["palabra_espanol"])
            .get()
            .await()

        if (existente.isEmpty) {
            // Crear nueva
            firestore.collection("palabras")
                .add(palabraData)
                .await()
            Log.d(TAG, "✅ Nueva palabra agregada: ${palabraData["palabra_espanol"]} → ${palabraData["palabra_pamie"]}")
        } else {
            // Actualizar existente
            val docId = existente.documents.first().id
            firestore.collection("palabras")
                .document(docId)
                .update(
                    "palabra_pamie", palabraData["palabra_pamie"],
                    "fuente", palabraData["fuente"],
                    "confianza", 0.95f,  // Mayor confianza por ser corregida,
                    "created_at", System.currentTimeMillis()
                )
                .await()
            Log.d(TAG, "✅ Palabra actualizada: ${palabraData["palabra_espanol"]}")
        }
    }

    /**
     * Guardar oración nueva en Firebase
     */
    private suspend fun guardarOracionEnCorpus(
        textoOriginal: String,
        traduccion: String,
        direccion: String,
        nombreUsuario: String
    ) {
        val esEspanolAPamiwa = direccion == "ES_TO_PAMIWA"

        val oracionData = hashMapOf(
            "espanol_presente" to if (esEspanolAPamiwa) textoOriginal.trim() else traduccion.trim(),
            "pamie_presente" to if (esEspanolAPamiwa) traduccion.trim() else textoOriginal.trim(),
            "espanol_pasado" to "",
            "pamie_pasado" to "",
            "espanol_futuro" to "",
            "pamie_futuro" to "",
            "familia" to "correcciones",
            "activo" to true,
            "fuente" to "correccion_$nombreUsuario",
            "confianza" to 0.9f,
            "created_at" to System.currentTimeMillis()
        )

        // Buscar si ya existe la oración
        val existente = firestore.collection("oraciones_completas")
            .whereEqualTo("espanol_presente", oracionData["espanol_presente"])
            .get()
            .await()

        if (existente.isEmpty) {
            // Crear nueva
            firestore.collection("oraciones_completas")
                .add(oracionData)
                .await()
            Log.d(TAG, "✅ Nueva oración agregada: ${oracionData["espanol_presente"]}")
        } else {
            // Actualizar existente
            val docId = existente.documents.first().id
            firestore.collection("oraciones_completas")
                .document(docId)
                .update(
                    "pamie_presente", oracionData["pamie_presente"],
                    "fuente", oracionData["fuente"],
                    "confianza", 0.95f,
                    "created_at", System.currentTimeMillis()
                )
                .await()
            Log.d(TAG, "✅ Oración actualizada: ${oracionData["espanol_presente"]}")
        }
    }

    /**
     * Guardar palabra en caché local (Room)
     */
    private suspend fun guardarPalabraEnCacheLocal(
        textoOriginal: String,
        traduccion: String,
        direccion: String,
        nombreUsuario: String
    ) {
        try {
            val esEspanolAPamiwa = direccion == "ES_TO_PAMIWA"
            val palabraEspanol = if (esEspanolAPamiwa)
                textoOriginal.lowercase().trim()
            else
                traduccion.lowercase().trim()

            val palabraPamie = if (esEspanolAPamiwa)
                traduccion.trim()
            else
                textoOriginal.trim()
            val existente = localDataSource.getPalabraPorEspanol(palabraEspanol)

            if (existente != null) {
                // ✅ Ya existe, ACTUALIZAR
                val actualizada = existente.copy(
                    palabra_pamie = palabraPamie,
                    fuente = "correccion_$nombreUsuario",
                    confianza = 0.95f,
                    created_at = System.currentTimeMillis()
                )
                localDataSource.updatePalabra(actualizada)
                Log.d(TAG, "✅ Palabra actualizada en Room: $palabraEspanol → $palabraPamie")
            } else {
                // ✅ No existe, CREAR
                val palabraEntity = PalabraEntity(
                    id = "corr_${System.currentTimeMillis()}",
                    palabra_espanol = palabraEspanol,
                    palabra_pamie = palabraPamie,
                    significado = "",
                    tipo_palabra = "general",
                    activo = true,
                    fuente = "correccion_$nombreUsuario",
                    confianza = 0.95f,
                    created_at = System.currentTimeMillis()
                )
                localDataSource.insertPalabra(palabraEntity)
                Log.d(TAG, "✅ Palabra creada en Room: $palabraEspanol → $palabraPamie")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando palabra en caché local", e)
        }
    }

    /**
     * Guardar oración en caché local (Room)
     */
    private suspend fun guardarOracionEnCacheLocal(
        textoOriginal: String,
        traduccion: String,
        direccion: String,
        nombreUsuario: String
    ) {
        try {
            val esEspanolAPamiwa = direccion == "ES_TO_PAMIWA"
            val espanolPresente = if (esEspanolAPamiwa)
                textoOriginal.trim()
            else
                traduccion.trim()

            val pamiePresente = if (esEspanolAPamiwa)
                traduccion.trim()
            else
                textoOriginal.trim()

            // 🔴 BUSCAR si existe en Room
            val existente = localDataSource.getOracionPorEspanol(espanolPresente)

            if (existente != null) {
                // ✅ Ya existe, ACTUALIZAR
                val actualizada = existente.copy(
                    pamie_presente = pamiePresente,
                    fuente = "correccion_$nombreUsuario",
                    confianza = 0.95f,
                    created_at = System.currentTimeMillis()
                )
                localDataSource.updateOracion(actualizada)
                Log.d(TAG, "✅ Oración actualizada en Room: $espanolPresente")
            } else {
                // ✅ No existe, CREAR
                val timestamp = System.currentTimeMillis()
                val oracionEntity = OracionEntity(
                    id = "corr_$timestamp",
                    id_base = "corr_base_$timestamp",
                    espanol_presente = espanolPresente,
                    pamie_presente = pamiePresente,
                    espanol_pasado = "",
                    pamie_pasado = "",
                    espanol_futuro = "",
                    pamie_futuro = "",
                    familia = "correcciones",
                    activo = true,
                    fuente = "correccion_$nombreUsuario",
                    confianza = 0.95f,
                    created_at = timestamp
                )
                localDataSource.insertOracion(oracionEntity)
                Log.d(TAG, "✅ Oración creada en Room: $espanolPresente")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando oración en caché local", e)
        }
    }

    /**
     * Verificar si el usuario actual puede editar
     */
    fun puedeEditar(): Boolean {
        val usuario = _usuarioActual.value ?: return false

        // Solo admin y traductor pueden editar
        val rolesPermitidos = listOf("admin", "traductor", "experto")

        return _estaLogueado.value &&
                usuario.activo &&
                usuario.rol.lowercase() in rolesPermitidos
    }
}