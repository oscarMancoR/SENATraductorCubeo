package com.sena.sennova.cubeoTranslator.PrincipalPage.UI.View


import androidx.compose.foundation.Image
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sena.sennova.cubeoTranslator.R

@Preview(
    showSystemUi = true)
@Composable
fun Vista2() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {


        ViewTop()
        Spacer(modifier = Modifier.size(16.dp))
        ViewMiddle1()
        ViewMiddle2()
        ViewBottom()
        LogoSena()

    }

}




@Composable
fun ViewTop() {
    var selector = true;
    val tamañoLetra = 20

    Row (
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
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

@Composable
fun ViewMiddle1() {
    // Estado para el texto
    var text by remember { mutableStateOf("") }
   Column (Modifier.fillMaxWidth()) {
       Row(
           modifier = Modifier
               .fillMaxWidth()
               .padding(8.dp),
           verticalAlignment = Alignment.CenterVertically
       ) {
           Box(
               modifier = Modifier.width(70.dp) // ajusta a tu gusto (ej. 96–120dp)
           ) {
               Text(
                   text = "Español",
                   fontSize = 16.sp,
                   modifier = Modifier.align(Alignment.TopStart),
                   maxLines = 1
               )
           }
           Spacer(modifier = Modifier.size(1.dp))

           Card(
               elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
               modifier = Modifier.weight(0.9f)
           ) {
               OutlinedTextField(
                   value = text,
                   onValueChange = { text = it },
                   textStyle = TextStyle(fontSize = 20.sp),
                   modifier = Modifier
                       .fillMaxWidth()
                       .padding(8.dp)
                       .height(100.dp),
                   colors = OutlinedTextFieldDefaults.colors(
                       focusedBorderColor = Color.Transparent,
                       unfocusedBorderColor = Color.Transparent
                   ),
                   placeholder = { Text(text = "Escribe aqui", fontSize = 20.sp) },
                   minLines = 3,
                   maxLines = 5

               )
           }
       }
   }
}
@Composable
fun ViewMiddle2() {
    // Estado para el texto
    var text by remember { mutableStateOf("") }
    Column (Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(70.dp) // ajusta a tu gusto (ej. 96–120dp)
            ) {
                Text(
                    text = "Pamiwa",
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.TopStart),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.size(1.dp))

            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.weight(0.9f)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(fontSize = 20.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    placeholder = { Text(text = "Traducción", fontSize = 20.sp) },
                    minLines = 3,
                    maxLines = 5

                )
            }
        }
    }
}


// SOLO agrega este código a tu Vista2.kt (no dupliques funciones)

@Composable
fun ViewBottom() {
    var selector by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Teclado: ${if (selector) "Español" else "Pamiwa"}",
            fontSize = 16.sp,
            modifier = Modifier.padding(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (selector) {
            TecladoEspanol()
        } else {
            TecladoPamiwa()
        }
    }
}

@Composable
fun TecladoEspanol() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Fila 1: Q-P
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").forEach { letra ->
                BotonTecla(letra, Modifier.weight(1f))
            }
        }

        // Fila 2: A-Ñ
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ").forEach { letra ->
                BotonTecla(letra, Modifier.weight(1f))
            }
        }

        // Fila 3: Z-M
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("z", "x", "c", "v", "b", "n", "m").forEach { letra ->
                BotonTecla(letra, Modifier.weight(1f))
            }
            BotonEspecial("←", Modifier.weight(1.5f))
        }

        // Fila 4: Espacio y puntuación
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BotonTecla(".", Modifier.weight(1f))
            BotonTecla(",", Modifier.weight(1f))
            BotonTecla("?", Modifier.weight(1f))
            BotonTecla("!", Modifier.weight(1f))
            BotonEspecial("Espacio", Modifier.weight(3f))
        }
    }
}

@Composable
fun TecladoPamiwa() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Fila 1: P-O
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("p", "a", "m", "i", "w", "ʉ", "e", "o").forEach { letra ->
                BotonTecla(letra, Modifier.weight(1f))
            }
        }

        // Fila 2: K-H
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("k", "t", "s", "n", "r", "l", "j", "h").forEach { letra ->
                BotonTecla(letra, Modifier.weight(1f))
            }
        }

        // Fila 3: B-C
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("b", "g", "d", "f", "v", "z", "x", "c").forEach { letra ->
                BotonTecla(letra, Modifier.weight(1f))
            }
        }

        // Fila 4: Ñ-Y + Borrar
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("ñ", "q", "y").forEach { letra ->
                BotonTecla(letra, Modifier.weight(1f))
            }
            BotonEspecial("←", Modifier.weight(1.5f))
        }

        // Fila 5: Espacio y puntuación
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BotonTecla(".", Modifier.weight(1f))
            BotonTecla(",", Modifier.weight(1f))
            BotonTecla("?", Modifier.weight(1f))
            BotonTecla("!", Modifier.weight(1f))
            BotonEspecial("Espacio", Modifier.weight(3f))
        }
    }
}

@Composable
fun BotonTecla(
    letra: String,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { println("Letra: $letra") },
        modifier = modifier.height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2196F3),
            contentColor = Color.Black
        )
    ) {
        Text(text = "h", fontSize = 50.sp)
    }
}

@Composable
fun BotonEspecial(
    funcion: String,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            when(funcion) {
                "←" -> println("Borrar")
                "Espacio" -> println("Espacio")
                else -> println("Función: $funcion")
            }
        },
        modifier = modifier.height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6200EA),
            contentColor = Color.White
        )
    ) {
        Text(
            text = funcion,
            fontSize = if (funcion == "Espacio") 14.sp else 18.sp
        )
    }
}

@Composable
fun LogoSena() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalArrangement = Arrangement.Center
    ){
        Image(
            painter = painterResource(id = R.drawable.logo_sennova),
            contentDescription = "Logo del sena y sennova",
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 10.dp)
                .height(150.dp) // Altura de la imagen
                ,
            contentScale = ContentScale.Crop
        )
    }

}