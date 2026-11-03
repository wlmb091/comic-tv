package com.pelican.comictv.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Parcelable

/** A ready-to-launch external player request; the screen starts it so the result comes back to the app. */
data class TvExternalLaunch(val intent:Intent,val playerName:String)

/** Builds the intents Stremio-style apps accept: VLC's own extras, and MX Player's, which Just Player also reads. */
object TvExternalPlayers {
    private val packages=mapOf("vlc" to listOf("org.videolan.vlc"),"mx" to listOf("com.mxtech.videoplayer.pro","com.mxtech.videoplayer.ad"),"just" to listOf("com.brouken.player"),"nova" to listOf("org.courville.nova"))
    fun installed(context:Context,player:String):String?=packages[player]?.firstOrNull { name ->
        runCatching { context.packageManager.getPackageInfo(name,0) }.isSuccess
    }
    fun available(context:Context)=TvVideoPrefs.PLAYERS.filter { it in TvVideoPrefs.INTERNAL||it=="ask"||installed(context,it)!=null }

    /** Null when the chosen app is not installed. [subtitle] is a loopback or HTTP address. */
    fun intent(context:Context,player:String,video:String,mime:String,title:String,positionMs:Long,subtitle:String?,subtitleName:String?):Intent? {
        val uri=Uri.parse(video)
        val base=Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).putExtra("title",title)
        return when(player) {
            "vlc"->{
                installed(context,"vlc")?:return null
                base.setComponent(ComponentName("org.videolan.vlc","org.videolan.vlc.gui.video.VideoPlayerActivity"))
                    .putExtra("from_start",false).putExtra("position",positionMs)
                    .apply { if(subtitle!=null)putExtra("subtitles_location",subtitle) }
            }
            "mx","just"->{
                val name=installed(context,player)?:return null
                base.setPackage(name).putExtra("return_result",true).putExtra("position",positionMs.coerceIn(0,Int.MAX_VALUE.toLong()).toInt())
                    .apply {
                        if(subtitle!=null) {
                            val subs=arrayOf<Parcelable>(Uri.parse(subtitle))
                            putExtra("subs",subs);putExtra("subs.enable",subs);putExtra("subs.name",arrayOf(subtitleName?:"Legenda"))
                        }
                    }
            }
            // Nova Video Player opens a plain VIEW intent; it brings its own FFmpeg decoders for chips whose hardware decoder shows no picture.
            "nova"->{
                val name=installed(context,"nova")?:return null
                base.setPackage(name)
            }
            else->Intent.createChooser(base.apply { if(subtitle!=null)putExtra("subtitles_location",subtitle) },"Abrir com")
        }
    }

    /** Position reported back by VLC (extra_position) or MX/Just (position), when the app returns one. */
    fun resultPosition(data:Intent?):Long?=data?.let { d ->
        when {
            d.hasExtra("extra_position")->d.getLongExtra("extra_position",-1)
            d.hasExtra("position")->d.getIntExtra("position",-1).toLong()
            else->null
        }?.takeIf { it>=0 }
    }
}

/**
 * Keeps the process alive while an external player reads the torrent through the loopback server.
 * Android stops background apps; this notification is the user-visible reason the transfer continues.
 */
class TvStreamService:Service() {
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==STOP){ (application as? com.pelican.comictv.ComicTvApplication)?.container?.tvPlayback?.stopExternal();stopSelf();return START_NOT_STICKY }
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Transmissão para player externo",NotificationManager.IMPORTANCE_LOW))
        val stop=android.app.PendingIntent.getService(this,1,Intent(this,TvStreamService::class.java).setAction(STOP),android.app.PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,CHANNEL).setSmallIcon(com.pelican.comictv.R.drawable.nav_tv)
            .setContentTitle("Comic TV").setContentText("Enviando o vídeo para o player externo.").setOngoing(true)
            .addAction(Notification.Action.Builder(null,"Parar",stop).build()).build()
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)startForeground(ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID,notification)
        return START_NOT_STICKY
    }
    companion object {
        private const val CHANNEL="tv-stream";private const val ID=7101;private const val STOP="com.pelican.comictv.tv.STOP_STREAM"
        fun start(context:Context)=runCatching { context.startForegroundService(Intent(context,TvStreamService::class.java)) }.isSuccess
        fun stop(context:Context){ runCatching { context.stopService(Intent(context,TvStreamService::class.java)) } }
    }
}

internal fun PackageManager.hasLauncherFor(intent:Intent)=resolveActivity(intent,0)!=null
