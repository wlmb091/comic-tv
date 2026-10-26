package com.pelican.comictv.ui

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.updates.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class AppUpdateSettingsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun primaryKeepsFocusAcrossCheckDownloadAndInstallAt130Percent() {
        var installs=0
        val source=object:AppUpdateSource {
            val update=UpdateManifest(58,"1.1.19",26,4,"a".repeat(64),"${UpdateManifest.RELEASE_BASE}/v1.1.19/Comic-TV-1.1.19.apk","Notas. ".repeat(200))
            override suspend fun check()=update
            override suspend fun download(update:UpdateManifest,progress:(Long)->Unit)=File("ready.apk").also{progress(4)}
            override suspend fun revalidate(file:File,update:UpdateManifest)=Unit
            override suspend fun markOffered(file:File)=Unit
            override suspend fun installationReturned(file:File)=Unit
            override suspend fun installedVersionCode()=57L
        }
        compose.setContent { ComicTvTheme(AppearanceOptions().withTextPercentage(130)) {
            val vm=remember { AppUpdateViewModel(source) }
            AppUpdateSettings(vm,{installs++})
        } }
        val primary=compose.onNodeWithTag("update-primary")
        primary.performSemanticsAction(SemanticsActions.RequestFocus){it()}.performKeyInput{pressKey(Key.DirectionCenter)}
        primary.assertIsFocused().assertTextContains("Baixar atualização").performKeyInput{pressKey(Key.DirectionCenter)}
        primary.assertIsFocused().assertTextContains("Instalar atualização").performKeyInput{pressKey(Key.DirectionCenter)}
        compose.runOnIdle {assertEquals(1,installs)}
    }
}
