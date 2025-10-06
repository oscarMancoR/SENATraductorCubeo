package com.sena.sennova.cubeoTranslator.Navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sena.sennova.cubeoTranslator.PrincipalPage.UI.View.PrincipalView
import com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel.EnhancedTranslationViewModel
import com.sena.sennova.cubeoTranslator.SplashScreen.SplashScreen
import com.sena.sennova.cubeoTranslator.PrincipalPage.UI.ViewModel.PrincipalPageViewModel


@Composable
fun NavigationScreen(
    principalPageViewModel: EnhancedTranslationViewModel
) {
    val navController = rememberNavController()
    NavHost(navController = navController,startDestination = AppScreen.SplashScreen.route) {
        composable(route = AppScreen.PrincipalView.route) {
            PrincipalView(principalPageViewModel)
        }
        composable(route = AppScreen.SplashScreen.route) {
            SplashScreen(navController)
        }

    }
}