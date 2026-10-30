package com.pelican.comictv.explorer

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import com.pelican.comictv.archive.ExplorerFiles
import java.io.File

/** Non-exported activity receives an explicit mutable platform callback; Android always confirms. */
class ExplorerSplitInstallActivity : ComponentActivity() {
    private var sessionId = -1
    @Volatile private var cancelled = false
    private var worker: Thread? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                cancelled = true; worker?.interrupt()
                if(sessionId>=0)runCatching { packageManager.packageInstaller.abandonSession(sessionId) }
                finish()
            }
        })
        setContentView(TextView(this).apply { text="Instalação de APKs divididos · aguarde a confirmação do Android"; setPadding(48,48,48,48) })
        if(intent.action==STATUS) { status(intent); return }
        if(state!=null) { sessionId=state.getInt("session",-1); return }
        worker = Thread({ try {
            val directory=File(requireNotNull(intent.getStringExtra("directory")))
            val root=File(cacheDir,"explorer-installs")
            require(ExplorerFiles.inside(root,directory) && directory.name.startsWith("bundle-") && directory.isDirectory)
            val files=directory.listFiles().orEmpty().toList()
            require(files.size in 1..100 && files.all { it.isFile && it.name.matches(Regex("split-\\d+\\.apk")) })
            val installer=packageManager.packageInstaller
            val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            sessionId=installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                files.forEach { file -> session.openWrite(file.name,0,file.length()).use { output ->
                    file.inputStream().use { input ->
                        val buffer = ByteArray(64*1024)
                        while (true) {
                            if (cancelled || Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                            val n = input.read(buffer); if (n < 0) break
                            output.write(buffer, 0, n)
                        }
                    };session.fsync(output)
                } }
                if (cancelled) throw java.io.InterruptedIOException()
                val callback=Intent(this,ExplorerSplitInstallActivity::class.java).setAction(STATUS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pending=PendingIntent.getActivity(this,sessionId,callback,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
            }
            directory.deleteRecursively()
        } catch(error:Exception) {
            if(sessionId>=0)runCatching { packageManager.packageInstaller.abandonSession(sessionId) }
            runOnUiThread { if (!isFinishing) finishResult(error.message ?: "Não foi possível iniciar a instalação.") }
        } }, "ComicTV-package-installer").also { it.start() }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);status(intent) }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("session",sessionId);super.onSaveInstanceState(outState) }
    private fun status(result:Intent) {
        when(result.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirmation=result.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                try { requireNotNull(confirmation);startActivityForResult(confirmation,1) }
                catch(error:Exception) { finishResult(error.message ?: "Confirmação Android indisponível.") }
            }
            PackageInstaller.STATUS_SUCCESS -> finishResult("Pacote instalado pelo Android.")
            else -> finishResult(result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Instalação cancelada ou recusada pelo Android.")
        }
    }
    @Deprecated("Platform confirmation result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==1 && resultCode==RESULT_CANCELED)finishResult("Instalação encerrada. Confira o resultado informado pelo Android.")
    }
    private fun finishResult(message:String) {
        android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show()
        setResult(RESULT_OK,Intent().putExtra("message",message));finish()
    }
    companion object { private const val STATUS="com.pelican.comictv.explorer.INSTALL_STATUS" }
}
