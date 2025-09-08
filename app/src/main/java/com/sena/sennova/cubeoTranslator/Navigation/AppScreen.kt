package com.sena.sennova.cubeoTranslator.Navigation

sealed class AppScreen(val route: String) {
    object PrincipalView : AppScreen("PrincipalView")
    object SplashScreen : AppScreen("SplashScreen")
}