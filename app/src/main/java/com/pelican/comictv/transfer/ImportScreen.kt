package com.pelican.comictv.transfer

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*

@Composable fun ImportScreen(busy: Boolean, message: String?, onMessage: (String?) -> Unit, onImport: (Uri) -> Unit) {
    val first = remember { FocusRequester() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onImport) }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ScreenTitle("Traga suas histórias", "Escolha como adicionar quadrinhos à sua biblioteca.")
        Column(Modifier.fillMaxWidth().background(TvColors.Panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Arquivo local", fontSize = 23.sp)
            Text("Selecione um CBZ ou ZIP no armazenamento da TV ou em um dispositivo USB.", fontSize = 17.sp, color = TvColors.Muted)
            TvButton(if (busy) "Importando… aguarde" else "Escolher arquivo", {
                if (!busy) try { picker.launch(arrayOf("*/*")) }
                catch (_: ActivityNotFoundException) { onMessage("Esta TV não oferece um seletor de arquivos. Use um dispositivo com seletor instalado; a transferência por rede será adicionada em um próximo incremento.") }
            }, Modifier.focusRequester(first), primary = true)
        }
        if (message != null) Text(message, color = TvColors.Amber, fontSize = 17.sp)
        Text("TRANSFERÊNCIAS · PRÓXIMOS INCREMENTOS", color = TvColors.Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          listOf(
            "Rede local" to "Envie pelo navegador do celular ou computador, com endereço e QR code na TV. Wi-Fi ou Ethernet.",
            "FTP" to "Receba arquivos por um servidor FTP temporário na TV.",
            "Pela web" to "Pareie com um portal de envio usando um código temporário ou QR code.",
        ).forEach { (title, description) ->
            Column(Modifier.weight(1f).background(TvColors.Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 21.sp)
                Text(description, color = TvColors.Muted, fontSize = 16.sp)
                Text("Em desenvolvimento", color = TvColors.Amber, fontSize = 13.sp)
            }
          }
        }
    }
}
