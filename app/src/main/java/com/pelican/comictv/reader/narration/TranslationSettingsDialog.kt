package com.pelican.comictv.reader.narration

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.library.LibraryDialog
import com.pelican.comictv.library.LibraryTextInput
import com.pelican.comictv.ui.*
import kotlinx.coroutines.*

data class LocalTranslationSettings(val address: String = "", val language: String = "en", val key: String = "") {
    companion object {
        fun read(context: Context): LocalTranslationSettings {
            val preferences = context.getSharedPreferences("local-translation", Context.MODE_PRIVATE)
            return LocalTranslationSettings(preferences.getString("address", "").orEmpty(),
                preferences.getString("language", "en") ?: "en", preferences.getString("key", "").orEmpty())
        }
    }
    suspend fun save(context: Context) = withContext(Dispatchers.IO) {
        check(context.getSharedPreferences("local-translation", Context.MODE_PRIVATE).edit()
            .putString("address", address).putString("language", language).putString("key", key).commit()) { "Não foi possível salvar o servidor local." }
    }
    fun client() = LocalTranslationClient(address, language, key)
}

@Composable internal fun TranslationSettingsDialog(initial: LocalTranslationSettings,
    onApply: (LocalTranslationSettings) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var address by remember { mutableStateOf(initial.address) }
    var key by remember { mutableStateOf(initial.key) }
    var language by remember { mutableStateOf(initial.language) }
    var languages by remember { mutableStateOf(listOf(TranslationLanguage("en", "Inglês"), TranslationLanguage("es", "Espanhol"),
        TranslationLanguage("fr", "Francês"), TranslationLanguage("de", "Alemão"), TranslationLanguage("it", "Italiano"))) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LibraryDialog("Tradução pelo computador", onDismiss) {
        Text("Use um servidor LibreTranslate na mesma rede. A página atual e até duas seguintes serão enviadas somente ao endereço local cadastrado. O computador precisa ficar ligado.", color = TvColors.Muted, fontSize = 16.sp)
        Text("Endereço · exemplo http://192.168.0.10:5000", fontSize = 16.sp)
        LibraryTextInput(address, { address = it.take(512) }, "Endereço do tradutor local", Modifier.focusRequester(first))
        Text("Chave de acesso do seu servidor (opcional)", fontSize = 16.sp)
        var focused by remember { mutableStateOf(false) }
        BasicTextField(key, { key = it.take(300) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }.semantics { contentDescription = "Chave do tradutor local" }
            .background(TvColors.Panel).border(if (focused) 2.dp else 1.dp, if (focused) TvColors.Amber else TvColors.Muted).padding(12.dp),
            singleLine = true, visualTransformation = PasswordVisualTransformation(), cursorBrush = SolidColor(TvColors.Amber),
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        TvButton("Idioma de origem: " + (languages.firstOrNull { it.code == language }?.label ?: language), {
            val index = languages.indexOfFirst { it.code == language }
            language = languages[(index + 1) % languages.size].code
        })
        TvButton(if (busy) "Conectando…" else "Testar conexão e consultar idiomas", {
            if (!busy) scope.launch {
                busy = true; message = null
                var client: LocalTranslationClient? = null
                try {
                    client = LocalTranslationClient(address, language, key)
                    languages = client.languages()
                    if (languages.none { it.code == language }) language = languages.first().code
                    message = "Conexão confirmada. Idiomas disponíveis atualizados."
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = e.message ?: "Não foi possível conectar ao computador." }
                finally { client?.close(); busy = false }
            }
        })
        message?.let { Text(it, color = TvColors.Amber, fontSize = 16.sp) }
        TvButton("Salvar servidor", {
            if (!busy) scope.launch {
                busy = true
                try {
                    val value = LocalTranslationSettings(address.trim(), language, key)
                    value.client().close() // Validate without sending text or requiring the server to be running.
                    value.save(context)
                    onApply(value); onDismiss()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = e.message ?: "Não foi possível salvar." }
                finally { busy = false }
            }
        })
        TvButton("Cancelar", onDismiss)
        Text("A tradução é automática. Sem conexão com o computador, use o texto original. Endereço e chave não entram no backup.", color = TvColors.Muted, fontSize = 15.sp)
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}
