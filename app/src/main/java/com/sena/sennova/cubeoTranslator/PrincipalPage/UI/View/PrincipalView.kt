package com.sena.sennova.cubeoTranslator.PrincipalPage.UI.View

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
fun SelectorButtonLanguage(selectorFromViewModel: Boolean, principalPageViewModel: PrincipalPageViewModel) {
    val tamañoLetra = 20
    var selector by rememberSaveable { mutableStateOf(false) }

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
            onClick = { selector = !selector }
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






