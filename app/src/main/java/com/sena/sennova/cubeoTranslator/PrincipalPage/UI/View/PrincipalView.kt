package com.sena.sennova.cubeoTranslator.PrincipalPage.UI.View

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrincipalView() {
   TopAppBar(title = { /*TODO*/ })
    Box(modifier = Modifier.fillMaxSize())  {

        topFrameLogo()
        selecctorButtonLanguage(true)
        boxTextTranslate()
        boxTextResult()
        keyboardView()
    }

}
//logos
fun topFrameLogo() {
    TODO("Not yet implemented")
}
//selector para cambiar de idiomas
fun selecctorButtonLanguage(selector: Boolean) {

}

//edittext donde se escribe el texto a traducir
fun boxTextTranslate(): String {
    return ""
}

//texto traducido
fun boxTextResult() {


}

//keyboard
fun keyboardView() {
    TODO("Not yet implemented")
}








