package com.pelican.comictv.reader

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*

@Composable internal fun AutomaticReadingControls(options: AutomaticReading, onStart: () -> Unit, onInterval: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton("Iniciar leitura automática", onStart)
            TvButton("Intervalo: ${options.secondsPerScreen}s", onInterval)
        }
        Text("Troca páginas ou rola o texto. Setas e OK pausam; não continua em segundo plano.", color = TvColors.Muted, fontSize = 15.sp)
    }
}
