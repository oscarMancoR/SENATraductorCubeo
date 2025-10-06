package com.sena.sennova.cubeoTranslator.SplashScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel.InitializationViewModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel.InitializationState
import com.sena.sennova.cubeoTranslator.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavController,
    initializationViewModel: InitializationViewModel = hiltViewModel()
) {
    val initState by initializationViewModel.initializationState.collectAsState()

    // Navegar cuando esté listo
    LaunchedEffect(initState) {
        if (initState is InitializationState.Ready) {
            delay(800) // Pequeño delay para UX
            navController.popBackStack()
            navController.navigate("PrincipalView")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Logo de la aplicación en la parte superior
        Image(
            painter = painterResource(id = R.drawable.grande_5x5),
            contentDescription = "Logo de la Aplicación",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
                .size(170.dp)
        )

        // Contenido central
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Nombre de la app
            Text(
                text = "NEKAMUI",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Traductor Español - Pamiwa",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Estado de sincronización
            when (val state = initState) {
                is InitializationState.Idle -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Iniciando...",
                        fontSize = 14.sp
                    )
                }

                is InitializationState.Syncing -> {
                    // Barra de progreso
                    LinearProgressIndicator(
                        progress = state.progress / 100f,
                        modifier = Modifier
                            .width(250.dp)
                            .height(6.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = state.message,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "${state.progress}%",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                is InitializationState.Ready -> {
                    Icon(
                        painter = painterResource(id = android.R.drawable.checkbox_on_background),
                        contentDescription = "Listo",
                        tint = Color.Green,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "¡Listo!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Green
                    )
                }

                is InitializationState.Error -> {
                    Icon(
                        painter = painterResource(id = android.R.drawable.stat_notify_error),
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Error al cargar datos",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = state.message,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Botones de acción
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { initializationViewModel.retrySync() }
                        ) {
                            Text("Reintentar")
                        }

                        Button(
                            onClick = {
                                // Navegar sin datos (modo degradado)
                                navController.popBackStack()
                                navController.navigate("PrincipalView")
                            }
                        ) {
                            Text("Continuar sin datos")
                        }
                    }
                }
            }
        }

        // Logo del SENA en la parte inferior
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mensaje informativo
            if (initState is InitializationState.Syncing) {
                Text(
                    text = "Primera carga: descargando traducciones\nLuego funcionará offline",
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Image(
                painter = painterResource(id = R.drawable.logo_sennova),
                contentDescription = "Logo SENA",
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}
