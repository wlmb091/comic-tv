package com.pelican.comictv.explorer

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.TvButton
import com.pelican.comictv.ui.TvColors

@Composable internal fun ProtectedArchiveAction(busy: Boolean, onMessage: (String?) -> Unit,
    onImportProtected: (List<Uri>, CharArray) -> Unit) {
    var selection by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var password by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.size > 100) onMessage("Escolha no máximo 100 partes.")
        else selection = uris
    }
    TvButton("Importar com senha ou partes .001", {
        if (!busy) try { picker.launch(arrayOf("*/*")) }
        catch (_: ActivityNotFoundException) { onMessage("O Android desta TV não oferece seleção de arquivos. Escolha um arquivo único sem senha ou use outra TV compatível.") }
    })
    if (selection.isNotEmpty()) com.pelican.comictv.ui.ComicDialog(onDismissRequest = { selection = emptyList(); password = "" }) {
        com.pelican.comictv.companion.CompanionDialogWindow { selection = emptyList(); password = "" }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
        Column(Modifier.fillMaxWidth().background(TvColors.Panel).padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Preparar ${selection.size} arquivo(s)", fontSize = 23.sp)
            Text("ZIP/CBZ, RAR/CBR ou 7z/CB7. Para partes binárias, escolha todas: .001, .002… A senha é usada somente agora; uma cópia CBZ sem senha será adicionada à biblioteca. Os originais são mantidos.", fontSize = 17.sp)
            Text("RAR multipart, ZIP .z01, DRM e RAR com nomes criptografados não são suportados. Deixe a senha vazia se o conjunto não exigir senha.", fontSize = 16.sp, color = TvColors.Muted)
            Text("Senha (opcional)", fontSize = 17.sp)
            BasicTextField(password, { password = it.take(1024) }, Modifier.fillMaxWidth().focusRequester(first).background(Color.Black).padding(16.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 19.sp), singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            TvButton("Preparar e importar", {
                val uris = selection; val secret = password.toCharArray()
                selection = emptyList(); password = ""
                try { onImportProtected(uris, secret) } catch (failure: Exception) { secret.fill('\u0000'); onMessage(failure.message) }
            }, primary = true)
            TvButton("Cancelar", { selection = emptyList(); password = "" })
        }
    }
}
