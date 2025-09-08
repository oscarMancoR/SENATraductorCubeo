package com.sena.sennova.cubeoTranslator.SplashScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.sena.sennova.cubeoTranslator.R
import kotlinx.coroutines.delay

@Composable

fun SplashScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Logo del SENA en la parte inferior de la pantalla
        Image(
            painter = painterResource(id = R.drawable.logo_sennova),
            contentDescription = "Logo SENA",
            modifier = Modifier
                .align(Alignment.BottomCenter) // Ubica el logo en la parte inferior
                .padding(bottom = 16.dp) // Agrega un pequeño margen inferior
        )

        // Nombre de la app en el centro de la pantalla
        Text(
            text = "NEKAMUI",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center) // Ubica el texto en el centro
        )

        // Logo de la aplicación en la parte superior
        Image(
            painter = painterResource(id = R.drawable.grande_5x5),
            contentDescription = "Logo de la Aplicación",
            modifier = Modifier
                .align(Alignment.TopCenter) // Ubica el logo en la parte superior
                .padding(top = 32.dp) // Agrega un pequeño margen superior
                .size(170.dp)
        )

        // Efecto para navegar después del delay
        LaunchedEffect(key1 = true) {
            delay(2000)
             navController.popBackStack()
             navController.navigate("PrincipalView")
        }
    }
}
