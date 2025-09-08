package com.sena.sennova.cubeoTranslator.PrincipalPage.UI.View


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.unit.dp

import com.sena.sennova.cubeoTranslator.PrincipalPage.Data.keyBoardDataClass
import com.sena.sennova.cubeoTranslator.R


@Composable
fun KeyBoardCreate() {
    val languaje = "pamiwa"
    // Determinar las teclas según el idioma
    val keyList = when (languaje) {
        "pamiwa" -> keyBoardPamiwa() // Teclado para el idioma Pamiwa
        "cubeo" -> keyBoardCubeo()   // Teclado para el idioma Cubeo
        else -> emptyList()          // Lista vacía por si el idioma no está definido
    }

    // Renderizar el teclado con las teclas seleccionadas
    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(7), // Ajusta el número de columnas según sea necesario
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            contentPadding = PaddingValues(4.dp)
        ) {
            items(keyList.size) { index ->
                keyImagenButton(keyList[index])
            }
        }

        // Renderizar teclas especiales
        keyboardSpecialButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(4.dp),
            onClickErase = { /* TODO */ },
            onClickEnter = { /* TODO */ },
            onClickSpace = { /* TODO */ }
        )
    }
}



@Composable
fun keyBoardPamiwa(): List<keyBoardDataClass> {
    return listOf(
        keyBoardDataClass(R.drawable.letra_a, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_a_1, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_c, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_ch, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_d, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_d1, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_e, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_e1, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_i, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_i1, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_j, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_m, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_n, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra__, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_o, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_o1, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_p, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_q, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_r, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_t, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_u, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.coma, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_u1, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_u2, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_u3, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_v, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_y, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.punto_final, onClick = { /*TODO*/ }),

        )
}

@Composable
fun keyBoardCubeo(): List<keyBoardDataClass> {
    return listOf(
        keyBoardDataClass(R.drawable.letra_a, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_a_1, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_a, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_a, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_a_1, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_a, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_a, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_a_1, onClick = { /*TODO*/ }),
        keyBoardDataClass(R.drawable.letra_a, onClick = { /*TODO*/ }),
    )
}

@Composable
fun keyboardSpecialButton(
    modifier: Modifier,
    onClickErase: () -> Unit,
    onClickEnter: () -> Unit,
    onClickSpace: () -> Unit,

    ) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onClickErase,
            modifier = Modifier
                .height(60.dp) // Mantenemos la altura igual

                .padding(2.dp)
                .aspectRatio(1f)
                .border(1.dp, Color.Black)
                .background(color = Color.LightGray),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Image(
                painter = painterResource(id = R.drawable.erase),
                contentDescription = "Erase",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }

        // Tecla de Espacio
        Button(
            onClick = onClickSpace,
            modifier = Modifier
                .height(60.dp) // Mantenemos la altura igual
                .width(180.dp)
                .padding(2.dp)
                .border(1.dp, Color.Black)
                .background(color = Color.LightGray),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            // Puedes poner una imagen o texto en la tecla de espacio
        }

        // Tecla Enter
        Button(
            onClick = onClickEnter,
            modifier = Modifier

                .height(60.dp) // Mantenemos la altura igual
                .padding(2.dp)
                .aspectRatio(1f)
                .border(1.dp, Color.Black)
                .background(color = Color.LightGray),

            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Image(
                painter = painterResource(id = R.drawable.enter),
                contentDescription = "Enter",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
    }

}


@Composable
fun keyImagenButton(letter: keyBoardDataClass) {
    Button(
        onClick = letter.onClick,
        modifier = Modifier
            .size(60.dp)
            .padding(1.dp)
            .aspectRatio(1f) // Para que las teclas sean cuadradas
            .border(1.dp, Color.Black)
            .background(color = Color.LightGray),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
    ) {
        Image(
            painter = painterResource(id = letter.imageRes),
            contentDescription = "letter",
            modifier = Modifier
                .fillMaxSize()
                .size(16.dp) // Ajusta el tamaño de la letra
                .padding(8.dp) // Aumenta el padding dentro del botón
        )
    }
}
