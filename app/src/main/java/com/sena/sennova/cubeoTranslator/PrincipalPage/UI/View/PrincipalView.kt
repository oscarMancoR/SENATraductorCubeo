package com.sena.sennova.cubeoTranslator.PrincipalPage.UI.View

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog

import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.TranslationDirection
import com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel.EnhancedTranslationViewModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel.KeyboardViewModel

import com.sena.sennova.cubeoTranslator.R
import kotlinx.coroutines.delay


// nueva versión


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrincipalView(
    principalPageViewModel: EnhancedTranslationViewModel = hiltViewModel(),

) {
    val traducciones by principalPageViewModel.traducciones.collectAsState()
    val text by principalPageViewModel.text.collectAsState()
    val selector by principalPageViewModel.selector.collectAsState()
    val uiState by principalPageViewModel.uiState.collectAsState()


    // Estados para manejar el teclado
    var isTextFieldFocused by remember { mutableStateOf(false) }
    var showCustomKeyboard by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Determinar qué teclado mostrar basado en la dirección de traducción
    val isPamiwaMode = uiState.direccion == TranslationDirection.PAMIWA_TO_ES
    val shouldShowPamiwaKeyboard = isPamiwaMode && isTextFieldFocused

    // ✅ NUEVO: Detectar cambios en la dirección y ocultar teclados
    LaunchedEffect(uiState.direccion) {
        // Cuando cambia la dirección, limpiar foco y ocultar teclados
        focusManager.clearFocus()
        keyboardController?.hide()
        showCustomKeyboard = false
        isTextFieldFocused = false
    }

    // Scroll suave cuando el campo está enfocado
    LaunchedEffect(isTextFieldFocused) {
        if (isTextFieldFocused) {
            delay(100)
            // Scroll mínimo solo para asegurar visibilidad
            scrollState.animateScrollTo(
                value = (scrollState.maxValue * 0.2f).toInt(),
                animationSpec = tween(200, easing = EaseOutCubic)
            )
        }
    }

    // Manejar el teclado personalizado
    LaunchedEffect(shouldShowPamiwaKeyboard) {
        if (shouldShowPamiwaKeyboard) {
            keyboardController?.hide() // Ocultar teclado del sistema
            delay(50)
            showCustomKeyboard = true
        } else {
            showCustomKeyboard = false
        }
    }

    // Limpiar traducciones si el texto está vacío
    LaunchedEffect(text) {
        if (text.isEmpty()) {
            principalPageViewModel.limpiarTraducciones()
        }
    }

    Scaffold(
        topBar = {
            // TopAppBar compacta siempre visible
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.traducir),
                            contentDescription = "icono logo",
                            modifier = Modifier.size(24.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "NEKAMUI",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp
                        )

                        // Indicador de estado del corpus
                        when (uiState.estadoCorpus) {
                            com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.CorpusLoadingState.LOADED -> {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.presence_online),
                                    contentDescription = "Corpus cargado",
                                    tint = Color.Green,
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                            com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.CorpusLoadingState.ERROR -> {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.presence_busy),
                                    contentDescription = "Error en corpus",
                                    tint = Color.Red,
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                            else -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(8.dp),
                                    strokeWidth = 1.dp
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Área reservada para el teclado personalizado
            if (showCustomKeyboard) {
                PamiwaKeyboard(
                    onKeyPress = { char ->
                        principalPageViewModel.guardarPalabra(text + char)
                    },
                    onBackspace = {
                        if (text.isNotEmpty()) {
                            principalPageViewModel.guardarPalabra(text.dropLast(1))
                        }
                    },
                    onSpace = {
                        principalPageViewModel.guardarPalabra(text + " ")
                    },
                    onDone = {
                        focusManager.clearFocus()
                        showCustomKeyboard = false
                    }
                )

            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        // Column principal con espaciado reducido
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp) // Espaciado mínimo
        ) {

            // Logo compacto
            CompactTopFrameLogo()

            // Selector de idiomas compacto
            CompactSelectorButtonLanguage(
                selector = selector,
                principalPageViewModel = principalPageViewModel,
                isTranslating = uiState.traduciendo
            )

            // Campo de texto compacto
            CompactBoxTextTranslate(
                text = text,
                isTranslating = uiState.traduciendo,
                direction = uiState.direccion,
                isPamiwaMode = isPamiwaMode,
                onTextChange = { newText ->
                    principalPageViewModel.guardarPalabra(newText)

                    if (newText.isEmpty()) {
                        principalPageViewModel.limpiarTraducciones()
                    } else {
                        principalPageViewModel.buscarTraducciones(newText)
                    }
                },
                onFocusChanged = { focused ->
                    isTextFieldFocused = focused
                },
                focusManager = focusManager,
                keyboardController = keyboardController
            )

            // Indicador de traducción compacto
            if (uiState.traduciendo) {
                CompactTranslationLoadingIndicator()
            }

            // Errores compactos
            uiState.error?.let { error ->
                CompactErrorCard(error = error)
            }

            // Resultado de traducción compacto
            CompactBoxTextResult(
                traducciones = traducciones,
                result = uiState.resultado,
                onCorrection = { correctedText ->
                    principalPageViewModel.submitUserCorrection(correctedText)
                }
            )

            // Espacio adicional solo si no hay teclado personalizado
            if (!showCustomKeyboard) {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun CompactTopFrameLogo() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_sennova),
            contentDescription = "Logo del sena y sennova",
            modifier = Modifier
                .height(80.dp) // Más compacto
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun CompactSelectorButtonLanguage(
    selector: Boolean,
    principalPageViewModel: EnhancedTranslationViewModel,
    isTranslating: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp), // Padding reducido
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp), // Padding interno reducido
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Idioma origen
            Button(
                modifier = Modifier.weight(0.4f),
                onClick = { /* Solo visual */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!selector)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                ),
                enabled = !isTranslating
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp) // Espaciado mínimo
                ) {
                    Text(
                        text = if (selector) "Pamiwa" else "Español",
                        fontSize = 14.sp, // Más pequeño
                        color = if (!selector) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Desde",
                        fontSize = 8.sp, // Más pequeño
                        color = if (!selector) Color.White.copy(alpha = 0.7f) else Color.Gray
                    )
                }
            }

            // Botón para intercambiar
            FilledIconButton(
                onClick = {
                    if (!isTranslating) {
                        principalPageViewModel.toggleSelector()
                    }
                },
                modifier = Modifier
                    .padding(horizontal = 4.dp) // Padding reducido
                    .size(40.dp), // Más pequeño
                enabled = !isTranslating
            ) {
                if (isTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.intercambiar),
                        contentDescription = "Intercambiar idioma",
                        modifier = Modifier
                            .size(24.dp) // Más pequeño
                            .clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Idioma destino
            Button(
                modifier = Modifier.weight(0.4f),
                onClick = { /* Solo visual */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selector)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                ),
                enabled = !isTranslating
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (!selector) "Pamiwa" else "Español",
                        fontSize = 16.sp,
                        color = if (selector) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Hacia",
                        fontSize = 10.sp,
                        color = if (selector) Color.White.copy(alpha = 0.7f) else Color.Gray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactBoxTextTranslate(
    text: String,
    isTranslating: Boolean,
    direction: com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.TranslationDirection,
    isPamiwaMode: Boolean,
    onTextChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp) // Padding reducido
    ) {
        Column {
            // Header compacto
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp), // Padding reducido
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Escribe en ${if (isPamiwaMode) "Pamiwa" else "español"}:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (text.isNotEmpty()) {
                        Text(
                            text = "${text.length}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Indicador de tipo de teclado
                    Text(
                        text = if (isPamiwaMode) "ɨ" else "ABC",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Campo de texto compacto
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = if (isPamiwaMode)
                            "Ejemplo: Táchi kóba, jáwé káwɨ?"
                        else
                            "Ejemplo: Hola, ¿cómo estás?",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                },
                textStyle = TextStyle(fontSize = 14.sp), // Más pequeño
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp) // Más compacto
                    .onFocusChanged { focusState ->
                        if (isPamiwaMode) {
                            if (focusState.isFocused) {
                                keyboardController?.hide()
                                onFocusChanged(true)
                            } else {
                                onFocusChanged(false)
                            }
                        } else {
                            onFocusChanged(false)
                        }
                    },

                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                enabled = true,
                readOnly = isPamiwaMode,
                maxLines = 3, // Reducido
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
            )
        }
    }
}

@Composable
fun CompactTranslationLoadingIndicator() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp), // Padding reducido
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp), // Más pequeño
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Traduciendo...",
                fontSize = 12.sp, // Más pequeño
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun CompactErrorCard(error: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp), // Padding reducido
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = android.R.drawable.stat_notify_error),
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp) // Más pequeño
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 11.sp, // Más pequeño
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
        }
    }
}

@Composable
fun CompactBoxTextResult(
    traducciones: List<String>,
    result: com.sena.sennova.cubeoTranslator.PrincipalPage.Data.model.TranslationResponse?,
    onCorrection: (String) -> Unit
) {
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var correctionText by remember { mutableStateOf("") }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp) // Padding reducido
    ) {
        Column {
            // Header compacto
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (result != null) MaterialTheme.colorScheme.secondaryContainer
                        else Color.LightGray
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp), // Padding reducido
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Traducción:",
                    fontSize = 12.sp, // Más pequeño
                    fontWeight = FontWeight.Medium,
                    color = if (result != null)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        Color.Gray
                )

                result?.let { res ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${(res.confianza * 100).toInt()}%",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        LinearProgressIndicator(
                            progress = res.confianza,
                            modifier = Modifier.width(30.dp), // Más pequeño
                            color = when {
                                res.confianza >= 0.8f -> Color.Green
                                res.confianza >= 0.6f -> Color.Red
                                else -> Color.Red
                            }
                        )
                    }
                }
            }

            // Contenido del resultado
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp) // Más compacto
                    .background(Color.White)
                    .padding(8.dp) // Padding reducido
            ) {
                if (traducciones.isNotEmpty()) {
                    Column {
                        Text(
                            text = traducciones.joinToString(" "),
                            fontSize = 14.sp, // Más pequeño
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        result?.let { res ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Método: ${res.metodo.name.replace("_", " ").lowercase()}",
                                fontSize = 9.sp, // Más pequeño
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    Text(
                        text = "La traducción aparecerá aquí...",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Botón de corrección compacto
            if (traducciones.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp), // Padding reducido
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            correctionText = traducciones.firstOrNull() ?: ""
                            showCorrectionDialog = true
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_edit),
                            contentDescription = "Corregir",
                            modifier = Modifier.size(12.dp) // Más pequeño
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "Corregir",
                            fontSize = 11.sp // Más pequeño
                        )
                    }
                }
            }
        }
    }

    // Dialog de corrección
    if (showCorrectionDialog) {
        AlertDialog(
            onDismissRequest = { showCorrectionDialog = false },
            title = { Text("Corregir traducción", fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "Si la traducción no es correcta, puedes corregirla:",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = correctionText,
                        onValueChange = { correctionText = it },
                        label = { Text("Traducción corregida", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 14.sp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCorrection(correctionText)
                        showCorrectionDialog = false
                    },
                    enabled = correctionText.trim().isNotEmpty()
                ) {
                    Text("Guardar", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCorrectionDialog = false }) {
                    Text("Cancelar", fontSize = 12.sp)
                }
            }
        )
    }
}

/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrincipalView(
    //principalPageViewModel: PrincipalPageViewModel
    principalPageViewModel: EnhancedTranslationViewModel = hiltViewModel()

) {
        // ✅ Funciona
    val traducciones by principalPageViewModel.traducciones.collectAsState(emptyList())
    val text by principalPageViewModel.text.collectAsState("")
    val selector by principalPageViewModel.selector.collectAsState(false)

    // Limpiar traducciones si el texto está vacío
    LaunchedEffect(text) {
        if (text.isEmpty()) {
            principalPageViewModel.limpiarTraducciones()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.traducir),
                        contentDescription = "icono logo",
                        modifier = Modifier.size(40.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEKAMUI",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TopFrameLogo()
            SelectorButtonLanguage(selector, principalPageViewModel)
            BoxTextTranslate(
                text = text,
                onTextChange = { newText ->
                    principalPageViewModel.guardarPalabra(newText)

                    if (newText.isEmpty()) {
                        principalPageViewModel.limpiarTraducciones()
                    } else {
                        principalPageViewModel.buscarTraducciones(newText)
                    }
                }
            )
            BoxTextResult(traducciones)
        }
    }
}

// Logos
@Preview
@Composable
fun TopFrameLogo() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(id = R.drawable.logo_sennova),
            contentDescription = "Logo del sena y sennova",
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 10.dp)
                .height(150.dp) // Altura de la imagen
                .align(Alignment.CenterHorizontally),
            contentScale = ContentScale.Crop
        )
    }
}

// Selector para cambiar de idiomas
@Composable
fun SelectorButtonLanguage(
    selectorFromViewModel: Boolean,
    principalPageViewModel: EnhancedTranslationViewModel) {
    val tamañoLetra = 20
    val selector = selectorFromViewModel

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, // Cambiado de Arrangement.Absolute.SpaceBetween
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            modifier = Modifier
                .weight(0.4f), // Removido fillMaxWidth() ya que weight() lo maneja
            onClick = { }
        ) {
            Text(text = if (selector) "Español" else "Pamiwa", fontSize = tamañoLetra.sp)
        }

        // Botón para cambiar de idioma, que es una imagen en forma de círculo
        Button(
            modifier = Modifier
                .weight(0.2f) // Removido fillMaxWidth()
                .padding(horizontal = 2.dp),
            onClick = { principalPageViewModel.toggleSelector()}
        ) {
            Image(
                painter = painterResource(id = R.drawable.intercambiar),
                contentDescription = "Intercambiar idioma",
                modifier = Modifier
                    .size(45.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Button(
            modifier = Modifier
                .weight(0.4f), // Removido fillMaxWidth()
            onClick = { }
        ) {
            Text(text = if (!selector) "Español" else "Pamiwa", fontSize = tamañoLetra.sp)
        }
    }
}

// EditText donde se escribe el texto a traducir
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxTextTranslate(text: String, onTextChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(100.dp)
    ) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text(text = "Traducir", fontSize = 20.sp) },
                textStyle = TextStyle(fontSize = 20.sp),
                modifier = Modifier.fillMaxSize(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun BoxTextResult(traducciones: List<String>) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(100.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray)
                .padding(8.dp)
        ) {
            Text(
                text = traducciones.joinToString(" "),
                fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Keyboard
@Composable
fun KeyboardView() {
    // TODO: Implementar keyboard view
    Text("Keyboard not implemented yet")
}


/*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel.PrincipalPageViewModel
import com.sena.sennova.cubeoTranslator.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrincipalView(
    principalPageViewModel: PrincipalPageViewModel
) {
    val traducciones by principalPageViewModel.traducciones.observeAsState(emptyList())
    val text by principalPageViewModel.text.observeAsState("")
    val selector by principalPageViewModel.selector.observeAsState(false)

    // Limpiar traducciones si el texto está vacío
    LaunchedEffect(text) {
        if (text.isEmpty()) {
            principalPageViewModel.limpiarTraducciones()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.traducir),
                        contentDescription = "icono logo",
                        modifier = Modifier.size(40.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEKAMUI",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TopFrameLogo()
            selecctorButtonLanguage(selector, principalPageViewModel)
            boxTextTranslate(
                text = text,
                onTextChange = { newText ->
                    principalPageViewModel.guardarPalabra(newText)

                    if (newText.isEmpty()) {
                        principalPageViewModel.limpiarTraducciones()
                    } else {
                        principalPageViewModel.buscarTraducciones(newText)
                    }
                }
            )
            boxTextResult(traducciones)
        }
    }
}

//logos
@Preview
@Composable
fun TopFrameLogo() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(id = R.drawable.logo_sennova),
            contentDescription = "Logo del sena y sennova",
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 10.dp)
                .height(150.dp) // Altura de la imagen
                .align(Alignment.CenterHorizontally),
            contentScale = ContentScale.Crop

        )
    }
}
//selector para cambiar de idiomas

@Composable
fun selecctorButtonLanguage(selector: Boolean, principalPageViewModel: PrincipalPageViewModel) {
    val tamañoLetra = 20
    var selector by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.2f),
            onClick = { }
        ) {
            Text(text = if (selector) "Español" else "Pamiwa", fontSize = tamañoLetra.sp)
        }

        // Botón para cambiar de idioma, que es una imagen en forma de círculo
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.1f)
                .padding(horizontal = 2.dp),
            onClick = { selector = !selector }
        ) {
            Image(
                painter = painterResource(id = R.drawable.intercambiar),
                contentDescription = "Intercambiar idioma",
                modifier = Modifier
                    .size(45.dp)
                    .align(Alignment.CenterVertically)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.2f),
            onClick = { }
        ) {
            Text(text = if (!selector) "Español" else "Pamiwa", fontSize = tamañoLetra.sp)
        }


    }
}

//edittext donde se escribe el texto a traducir
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun boxTextTranslate(text: String, onTextChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(100.dp)
    ) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text(text = "Traducir", fontSize = 20.sp) },
                textStyle = TextStyle(fontSize = 20.sp),
                modifier = Modifier.fillMaxSize(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

        }
    }
}

@Composable
fun boxTextResult(traducciones: List<String>) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(100.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray)
                .padding(8.dp)
        ) {
            Text(
                text = traducciones.joinToString(" "),
                fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}



//keyboard
@Composable
fun keyboardView() {
    TODO("Not yet implemented")
}

*/


*/



