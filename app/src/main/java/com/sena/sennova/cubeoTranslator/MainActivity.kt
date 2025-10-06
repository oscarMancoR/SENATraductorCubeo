package com.sena.sennova.cubeoTranslator

import android.inputmethodservice.Keyboard
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.FirebaseApp
import com.sena.sennova.cubeoTranslator.Navigation.NavigationScreen
import com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel.EnhancedTranslationViewModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel.InitializationViewModel
import com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel.PrincipalPageViewModel
import com.sena.sennova.cubeoTranslator.ui.theme.CubeoTraslatorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val principalPageViewModel: EnhancedTranslationViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "Starting onCreate")

        try {
            // Inicializar Firebase de forma segura
            initializeFirebase()

            enableEdgeToEdge()

            setContent {
                CubeoTraslatorTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavigationScreen(principalPageViewModel)
                    }
                }
            }

            Log.d("MainActivity", "onCreate completed successfully")

        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
            e.printStackTrace()

            // Fallback UI en caso de error
            setContent {
                CubeoTraslatorTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Mostrar mensaje de error o UI básica
                        ErrorScreen(error = e.message ?: "Error desconocido")
                    }
                }
            }
        }
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val app = FirebaseApp.initializeApp(this)
                Log.d("MainActivity", "Firebase initialized: ${app?.name}")
            } else {
                Log.d("MainActivity", "Firebase already initialized")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Firebase initialization failed: ${e.message}", e)
            // No re-lanzar la excepción, permitir que la app continúe sin Firebase
        }
    }
}

@Composable
fun ErrorScreen(error: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error al inicializar la aplicación",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

/*
        // Inicializar Firebase (esto es opcional si ya está configurado)
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        enableEdgeToEdge()
        setContent {
            CubeoTraslatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavigationScreen(principalPageViewModel)//pamiwa
                }
            }/*
        }
    }
}

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val app = FirebaseApp.initializeApp(this)
                Log.d("MainActivity", "Firebase initialized: ${app?.name}")
            } else {
                Log.d("MainActivity", "Firebase already initialized")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Firebase initialization failed: ${e.message}", e)
            // No re-lanzar la excepción, permitir que la app continúe sin Firebase
        }
    }
}

@Composable
fun ErrorScreen(error: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error al inicializar la aplicación",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}*/





   */