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

@Composable fun ImportScreen(busy: Boolean, message: String?, onMessage: (String?) -> Unit, onImport: (Uri) -> Unit, onLocalTransfer: () -> Unit, onFtpTransfer: () -> Unit, onCancelImport: () -> Unit = {}, onBrowser: () -> Unit = {}, onExplore: () -> Unit = {}, onCompanion: () -> Unit = {}, onImportProtected: (List<Uri>,CharArray)->Unit = {_,secret->secret.fill('\u0000')}) {
    val scroll = rememberScrollState()
    val first = preferredContentEntryModifier(prepare = { scroll.scrollTo(0) })
    val initial = remember { FocusRequester() }
    val enteringSection = LocalSectionContentEntry.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onImport) }
    LaunchedEffect(Unit) { if (!enteringSection) initial.requestFocus() }
    Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ScreenTitle("Traga suas histórias", "Escolha como adicionar quadrinhos à sua biblioteca.")
        Column(Modifier.fillMaxWidth().background(TvColors.Panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Arquivo local", fontSize = 23.sp)
            Text("Selecione CBZ, ZIP, CBR, RAR, 7z, CB7, PDF ou EPUB no armazenamento da TV ou em um dispositivo USB.", fontSize = 17.sp, color = TvColors.Muted)
            Text("Coleção ZIP/RAR: as HQs dentro do pacote são importadas separadamente. Até 100 HQs e 1 GiB por pacote; cada HQ deve estar em seu próprio arquivo.", fontSize = 17.sp, color = TvColors.Muted)
            TvButton(if (busy) "Importando… aguarde" else "Escolher arquivo", {
                if (!busy) try { picker.launch(arrayOf("*/*")) }
                catch (_: ActivityNotFoundException) { onMessage("Esta TV não oferece um seletor de arquivos. Use a opção Enviar pela rede local para transferir pelo celular.") }
            }, first.focusRequester(initial), primary = true)
        }
        com.pelican.comictv.explorer.ProtectedArchiveAction(busy, onMessage, onImportProtected)
        TvButton("Explorar arquivos · TV / USB", { if (!busy) onExplore() })
        TvButton("Conectar celular · iPhone / Android", {if(!busy)onCompanion()})
        if (busy) TvButton("Interromper importação", onCancelImport)
        if (message != null) Text(message, color = TvColors.Amber, fontSize = 17.sp)
        TvButton("Enviar pela rede local · Wi-Fi / Ethernet", { if (!busy) onLocalTransfer() }, primary = true)
        TvButton("Receber por FTP", { if (!busy) onFtpTransfer() })
        TvButton("Navegar e baixar arquivo", { if (!busy) onBrowser() })
        Text("TRANSFERÊNCIAS · PRÓXIMOS INCREMENTOS", color = TvColors.Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          listOf(
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
