package com.pelican.comictv.transfer

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CompanionServerTest {
    @Test fun tvPlaybackCommandsAreBoundedAndNeedThePrivateSession() {
        val root=Files.createTempDirectory("companion-tv").toFile();val commands=mutableListOf<CompanionCommand>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{}".toByteArray())},command={commands+=it})
        try {
            server.open();assertEquals(200,request(server.url+"/tv-state").first)
            for(action in listOf("tv-pause","tv-resume","tv-stop","tv-open"))assertEquals(200,request(server.url+"/command","""{"action":"$action"}""").first)
            assertEquals(200,request(server.url+"/command","""{"action":"tv-seek","positionMs":42000}""").first)
            assertEquals(400,request(server.url+"/command","""{"action":"tv-seek","positionMs":-1}""").first)
            assertEquals(400,request(server.url+"/command","""{"action":"tv-addon-preview","text":"file:///etc/passwd"}""").first)
            assertEquals(400,request(server.url+"/command","""{"action":"tv-addon-install","id":"../bad"}""").first)
            assertEquals(200,request(server.url+"/command","""{"action":"tv-addon-preview","text":"stremio://example.org/token/manifest.json"}""").first)
            assertEquals(200,request(server.url+"/command","""{"action":"tv-addon-install","id":"12345678-1234-1234-1234-123456789abc"}""").first)
            assertEquals(42000L,commands.first{it.action=="tv-seek"}.positionMs)
            assertEquals(404,request(server.url.substringBefore("/s/")+"/s/wrong/tv-state").first)
        } finally {server.close();root.deleteRecursively()}
    }
    @Test fun tvSubtitleAudioAndPrefsCommandsAreBounded() {
        val root=Files.createTempDirectory("companion-tv-subs").toFile();val commands=mutableListOf<CompanionCommand>();val files=mutableListOf<Pair<String,Int>>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{}".toByteArray())},command={commands+=it},
            subtitle={name,bytes->files+=name to bytes.size})
        try {
            server.open()
            for(text in listOf("off","e:3:1","x:12"))assertEquals(200,request(server.url+"/command","""{"action":"tv-subtitle","text":"$text"}""").first)
            assertEquals(400,request(server.url+"/command","""{"action":"tv-subtitle","text":"https://example.org/a.srt"}""").first)
            assertEquals(200,request(server.url+"/command","""{"action":"tv-audio","text":"a:2"}""").first)
            assertEquals(400,request(server.url+"/command","""{"action":"tv-audio","text":"x:1"}""").first)
            assertEquals(200,request(server.url+"/command","""{"action":"tv-subtitle-delay","delta":-25}""").first)
            assertEquals(400,request(server.url+"/command","""{"action":"tv-subtitle-delay","delta":601}""").first)
            assertEquals(200,request(server.url+"/command","""{"action":"tv-prefs","text":"{\"player\":\"vlc\",\"subtitles\":{\"sizePercent\":120}}"}""").first)
            assertEquals(400,request(server.url+"/command","""{"action":"tv-prefs","text":"not json"}""").first)
            fun upload(name:String,bytes:ByteArray):Int{val c=URL(server.url+"/tv-subtitle?name="+name).openConnection() as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/octet-stream");c.outputStream.use{it.write(bytes)};return c.responseCode}
            assertEquals(200,upload("ep.pt-BR.srt","1\n00:00:01,000 --> 00:00:02,000\nOlá\n".toByteArray()))
            assertEquals(400,upload("video.mkv",ByteArray(10)))
            // Refused before the body is read: the server may close while the phone is still sending.
            assertEquals(400,runCatching{upload("big.srt",ByteArray(5*1024*1024+1))}.getOrDefault(400))
            assertEquals(listOf("ep.pt-BR.srt"),files.map{it.first})
            assertEquals(-25,commands.first{it.action=="tv-subtitle-delay"}.delta)
        } finally {server.close();root.deleteRecursively()}
    }
    /** The server is an allowlist: a phone button whose action is missing here fails as "Comando inválido". */
    @Test fun rotateBrowserArrowsAndPinchReachTheApp() {
        val root=Files.createTempDirectory("companion-actions").toFile();val commands=mutableListOf<CompanionCommand>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{}".toByteArray())},command={commands+=it})
        try {
            server.open()
            for(body in listOf("""{"action":"rotate"}""","""{"action":"browser-up"}""","""{"action":"browser-down"}""","""{"action":"browser-left"}""","""{"action":"browser-right"}""","""{"action":"browser-ok"}""","""{"action":"reader-pinch","zoom":2,"x":0.5,"y":0.25,"dx":-0.1,"dy":0.1}"""))assertEquals(body,200,request(server.url+"/command",body).first)
            assertEquals(7,commands.size)
            for(body in listOf("""{"action":"reader-view","zoom":3.1,"x":0.5,"y":0.5}""","""{"action":"reader-view","zoom":2,"x":1.5,"y":0.5}""","""{"action":"image-adjust","id":"a-1","text":"1:9"}""","""{"action":"image-adjust","id":"../x","text":""}""","""{"action":"reader-adjust","text":"lixo"}""","""{"action":"reader-drag","x":1.5}""","""{"action":"reader-pinch","zoom":3.1,"x":0.5,"y":0.5}""","""{"action":"reader-pinch","zoom":2,"x":1.5,"y":0.5}""","""{"action":"reader-pinch","zoom":2,"x":0.5,"y":0.5,"dx":2}"""))assertEquals(body,400,request(server.url+"/command",body).first)
        }finally{server.close();root.deleteRecursively()}
    }
    @Test fun strokesAreBoundedAndReachTheApp() {
        val root=Files.createTempDirectory("companion-stroke").toFile();val strokes=mutableListOf<CompanionStroke>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{}".toByteArray())},command={},stroke={strokes+=it})
        try {
            server.open()
            assertEquals(200,request(server.url+"/stroke","""{"id":"a-1","page":2,"kind":"begin","color":"#ff0000","size":0.02,"opacity":0.5,"points":[0.1,0.2]}""").first)
            assertEquals(listOf(.1f,.2f),strokes.single().points)
            for(body in listOf("""{"id":"../a","page":2,"kind":"begin"}""","""{"id":"a","page":2,"kind":"other"}""","""{"id":"a","kind":"move","color":"red"}""","""{"id":"a","kind":"move","points":[0.1]}""","""{"id":"a","kind":"move","points":[9,0]}""","""{"id":"a","kind":"move","size":2}"""))assertEquals(body,400,request(server.url+"/stroke",body).first)
            assertEquals(1,strokes.size)
        }finally{server.close();root.deleteRecursively()}
    }
    @Test fun drawingsAcceptOnlyAPngInsideTheLayer() {
        val root=Files.createTempDirectory("companion-drawing").toFile();val seen=mutableListOf<Pair<CompanionDrawing,ByteArray>>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("image/png",ByteArray(0),headers=mapOf("X-Drawing-Page" to "10x20"))},command={},drawing={d,b->seen+=d to b})
        fun post(path:String,bytes:ByteArray):Int{val c=URL(server.url+path).openConnection() as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","image/png");c.outputStream.use{it.write(bytes)};return c.responseCode}
        val png=byteArrayOf(0x89.toByte(),0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,1,2)
        try {
            server.open()
            assertEquals(200,post("/drawing/patch/a-1/2?x=1&y=2&w=3&h=4&lw=100&lh=200",png))
            assertEquals(CompanionDrawing("patch","a-1",2,1,2,3,4,100,200),seen.single().first);assertArrayEquals(png,seen.single().second)
            assertEquals(200,post("/drawing/save/a-1/2?lw=100&lh=200&pw=800&ph=1600",png))
            assertEquals(400,post("/drawing/patch/a-1/2?x=99&y=2&w=3&h=4&lw=100&lh=200",png))
            assertEquals(400,post("/drawing/patch/a-1/2?x=1&y=2&w=3&h=4&lw=100&lh=200",byteArrayOf(1,2,3,4,5,6,7,8,9,10)))
            assertEquals(400,post("/drawing/save/a-1/2?lw=5000&lh=5000&pw=800&ph=1600",png))
            assertEquals(400,post("/drawing/other/a-1/2",png))
            assertEquals(2,seen.size)
            val c=URL(server.url+"/drawing/a-1/2").openConnection() as HttpURLConnection
            assertEquals(200,c.responseCode);assertEquals("10x20",c.getHeaderField("X-Drawing-Page"))
        }finally{server.close();root.deleteRecursively()}
    }
    @Test fun layerFilesNeedTheirSignature() {
        val root=Files.createTempDirectory("companion-layers").toFile();val seen=mutableListOf<ByteArray>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/octet-stream",ByteArray(0))},command={},drawingLayers={_,_,input->seen+=input.readBytes()})
        fun post(bytes:ByteArray):Int{val c=URL(server.url+"/drawing/layers/a-1/2").openConnection() as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/octet-stream");c.outputStream.use{it.write(bytes)};return c.responseCode}
        try {
            server.open()
            assertEquals(200,post("CTVLdata".toByteArray()));assertArrayEquals("CTVLdata".toByteArray(),seen.single())
            assertEquals(400,post("nope".toByteArray()));assertEquals(1,seen.size)
            assertEquals(200,(URL(server.url+"/drawing-layers/a-1/2").openConnection() as HttpURLConnection).responseCode)
        }finally{server.close();root.deleteRecursively()}
    }
    @Test fun themeCommandAcceptsOnlyTheFiveSelectableThemes() {
        val root=Files.createTempDirectory("companion-theme").toFile();val commands=mutableListOf<CompanionCommand>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{}".toByteArray())},command={commands+=it})
        try {
            server.open()
            for(name in listOf("Amber","Flix","Prime","Max","Cinema"))assertEquals(name,200,request(server.url+"/command","""{"action":"theme","text":"$name"}""").first)
            assertEquals(listOf("Amber","Flix","Prime","Max","Cinema"),commands.map{it.text})
            for(body in listOf("""{"action":"theme","text":"Green"}""","""{"action":"theme","text":""}""","""{"action":"theme"}"""))assertEquals(body,400,request(server.url+"/command",body).first)
        }finally{server.close();root.deleteRecursively()}
    }
    @Test fun browserPageSliderAndPinchAreBounded() {
        val root=Files.createTempDirectory("companion-browser").toFile();val commands=mutableListOf<CompanionCommand>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{}".toByteArray())},command={commands+=it})
        try {
            server.open()
            for(body in listOf("""{"action":"browser-scroll-to","dy":0.5}""","""{"action":"browser-pinch","zoom":1.2,"x":0.5,"y":0.25,"dx":-0.1,"dy":0.1}"""))assertEquals(body,200,request(server.url+"/command",body).first)
            assertEquals(2,commands.size)
            for(body in listOf("""{"action":"browser-scroll-to","dy":1.5}""","""{"action":"browser-pinch","zoom":2.5,"x":0.5,"y":0.5}""","""{"action":"browser-pinch","zoom":1.2,"x":1.5,"y":0.5}"""))assertEquals(body,400,request(server.url+"/command",body).first)
        }finally{server.close();root.deleteRecursively()}
    }
    @Test fun useRenewsTheSessionUpToItsLimit() {
        val root=Files.createTempDirectory("companion-idle").toFile();var now=1L
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{}".toByteArray())},command={},clock={now},lifetimeMillis=100,maxLifetimeMillis=250)
        try {
            server.open()
            now=91;assertEquals(200,request(server.url+"/reader-state").first)
            now=181;assertEquals(200,request(server.url+"/reader-state").first);assertFalse(server.expired())
            now=251;assertTrue(server.expired());assertEquals(410,request(server.url+"/reader-state").first)
        }finally{server.close();root.deleteRecursively()}
    }
    @Test fun statePollKeepsAnsweringWhilePagesLoad() {
        val root=Files.createTempDirectory("companion-busy").toFile();val release=java.util.concurrent.CountDownLatch(1);val loading=java.util.concurrent.CountDownLatch(2)
        val server=CompanionServer("127.0.0.1",root,"panel",get={path->if(path!="/reader-state"){loading.countDown();release.await()};CompanionResource("application/json","{}".toByteArray())},command={})
        try {
            server.open()
            val pages=List(2){index->kotlin.concurrent.thread{runCatching{request(server.url+"/page/book/$index")}}}
            assertTrue(loading.await(5,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(409,request(server.url+"/library").first)
            assertEquals(200,request(server.url+"/reader-state").first)
            release.countDown();pages.forEach{it.join(5000)}
        }finally{release.countDown();server.close();root.deleteRecursively()}
    }
    @Test fun coversHaveTheirOwnSlotsAndAreCachedBrieflyOnThePhone() {
        val root=Files.createTempDirectory("companion-covers").toFile();val release=java.util.concurrent.CountDownLatch(1);val loading=java.util.concurrent.CountDownLatch(2)
        val server=CompanionServer("127.0.0.1",root,"panel",get={path->if(path.startsWith("/page/")){loading.countDown();release.await()};CompanionResource("image/jpeg",byteArrayOf(1,2,3))},command={})
        try {
            server.open()
            // Two slow pages take every general slot; covers still answer, and more than two at once.
            val pages=List(2){index->kotlin.concurrent.thread{runCatching{request(server.url+"/page/book/$index")}}}
            assertTrue(loading.await(5,java.util.concurrent.TimeUnit.SECONDS))
            val answers=java.util.Collections.synchronizedList(mutableListOf<Int>())
            val covers=List(4){index->kotlin.concurrent.thread{answers+=request(server.url+(if(index%2==0)"/cover/book-$index" else "/saga-cover/book-$index")).first}}
            covers.forEach{it.join(8000)}
            assertEquals(List(4){200},answers.toList())
            assertEquals(409,request(server.url+"/file/book").first)
            val connection=URL(server.url+"/cover/book-1").openConnection() as HttpURLConnection
            try{assertEquals("private, max-age=300",connection.getHeaderField("Cache-Control"))}finally{connection.disconnect()}
            val other=URL(server.url+"/library").openConnection() as HttpURLConnection
            try{assertEquals("no-store",other.getHeaderField("Cache-Control"))}finally{other.disconnect()}
            release.countDown();pages.forEach{it.join(5000)}
        }finally{release.countDown();server.close();root.deleteRecursively()}
    }
    @Test fun readerMenuChapterAndBrowserCommandsAreBounded() {
        val root=Files.createTempDirectory("companion-reader").toFile();val commands=mutableListOf<CompanionCommand>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{\"active\":false}".toByteArray())},command={commands+=it})
        try {
            server.open()
            for(body in listOf("""{"action":"reader-arrow","text":"meio"}""","""{"action":"open-chapter","id":"../x","page":1}""","""{"action":"reader-option","text":"apagar"}""","""{"action":"reader-option"}""","""{"action":"browser-open","text":"  "}"""))assertEquals(body,400,request(server.url+"/command",body).first)
            for(body in listOf("""{"action":"reader-arrow","text":"left"}""","""{"action":"open-chapter","id":"l3","page":1}""","""{"action":"ok-long"}""","""{"action":"reader-option","text":"fit"}""","""{"action":"reader-option","text":"exit"}""","""{"action":"browser-open","text":"google.com"}"""))assertEquals(body,200,request(server.url+"/command",body).first)
            assertEquals(6,commands.size)
            for(endpoint in listOf("/home-sources","/source-cover/l3","/source-cover/c120","/home-more/l/48","/home-more/c/0","/chapters/online-a","/saga-cover/online-a"))assertEquals(endpoint,200,request(server.url+endpoint).first)
            for(endpoint in listOf("/source-cover/x9","/home-more/x/1","/home-more/l/","/home-more/l/12345","/home-more/l/-1"))assertEquals(endpoint,404,request(server.url+endpoint).first)
        } finally {server.close()}
    }

    @Test fun contextualReaderAndPointerCommandsAreBoundedAndMirrorIsAbsent() {
        val root=Files.createTempDirectory("companion-reader").toFile();val commands=mutableListOf<CompanionCommand>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{\"active\":false}".toByteArray())},command={commands+=it})
        try {
            server.open()
            assertEquals(200,request(server.url+"/reader-state").first)
            assertEquals(404,request(server.url+"/screen").first)
            for(body in listOf("""{"action":"reader-view","zoom":3,"x":0.25,"y":1}""","""{"action":"reader-adjust","text":""}""","""{"action":"reader-drag","x":-0.4}""","""{"action":"reader-pan","x":-0.12,"y":1}""","""{"action":"pointer","dx":-1,"dy":0.5}""","""{"action":"reader-page","page":4999}""","""{"action":"reader-turn","delta":-1}""","""{"action":"reader-zoom","zoom":2.25}"""))assertEquals(body,200,request(server.url+"/command",body).first)
            for(body in listOf("""{"action":"browser-pointer","dx":0,"dy":0.1}""","""{"action":"home"}""","""{"action":"settings","readerSensitivity":2,"pointerSensitivity":0.25}""","""{"action":"browser-scroll","dx":0,"dy":0.2}""","""{"action":"browser-zoom","zoom":1.5}""","""{"action":"browser-press"}""","""{"action":"browser-release"}""","""{"action":"browser-context"}"""))assertEquals(body,200,request(server.url+"/command",body).first)
            assertEquals(16,commands.size)
            for(body in listOf("""{"action":"settings","readerSensitivity":0}""","""{"action":"settings","zoomSensitivity":4}""","""{"action":"browser-scroll","dy":2}""","""{"action":"browser-zoom","zoom":2.1}"""))assertEquals(body,400,request(server.url+"/command",body).first)

            for(body in listOf("""{"action":"reader-pan","x":-1.1}""","""{"action":"pointer","dx":2}""","""{"action":"reader-page","page":-1}""","""{"action":"reader-page","page":5000}""","""{"action":"reader-turn","delta":2}""","""{"action":"reader-zoom","zoom":0.9}""","""{"action":"reader-zoom","zoom":3.1}"""))assertEquals(body,400,request(server.url+"/command",body).first)
        }finally{server.close();root.deleteRecursively()}
    }
    @Test fun cursorHistoryReaderScrollAndLongOkCommandsAreBounded() {
        val root=Files.createTempDirectory("companion-complete").toFile();val commands=mutableListOf<CompanionCommand>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{\"active\":false}".toByteArray())},command={commands+=it})
        try {
            server.open()
            for(body in listOf("""{"action":"cursor","cursorSize":50,"cursorStyle":0}""","""{"action":"cursor","cursorSize":300,"cursorStyle":2}"""))assertEquals(body,200,request(server.url+"/command",body).first)
            for(body in listOf("""{"action":"reader-scroll-to","dy":0.5}""","""{"action":"browser-history","delta":-1}""","""{"action":"browser-history","delta":1}"""))assertEquals(body,200,request(server.url+"/command",body).first)
            for(body in listOf("""{"action":"reader-scroll-to","dy":1.5}""","""{"action":"reader-scroll-to","dy":-0.1}""","""{"action":"browser-history","delta":0}""","""{"action":"browser-history","delta":2}"""))assertEquals(body,400,request(server.url+"/command",body).first)
            for(body in listOf("""{"action":"cursor","cursorSize":49,"cursorStyle":0}""","""{"action":"cursor","cursorSize":301}""","""{"action":"cursor","cursorStyle":3}""","""{"action":"cursor","cursorSize":100,"cursorStyle":-1}"""))assertEquals(body,400,request(server.url+"/command",body).first)
            assertEquals(5,commands.size)
        }finally{server.close();root.deleteRecursively()}
    }
    private fun request(url:String,body:String?=null,type:String="application/json"):Pair<Int,String> {
        val c=URL(url).openConnection() as HttpURLConnection
        c.connectTimeout=2000;c.readTimeout=5000
        try {
            if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type",type);val b=body.toByteArray();c.setFixedLengthStreamingMode(b.size);c.outputStream.use{it.write(b)}}
            val status=c.responseCode;return status to (if(status>=400)c.errorStream else c.inputStream).bufferedReader().use{it.readText()}
        } finally {c.disconnect()}
    }
    @Test fun sessionIsPrivateAndCommandsAreValidated() {
        val root=Files.createTempDirectory("companion").toFile();val commands=mutableListOf<CompanionCommand>()
        val server=CompanionServer("127.0.0.1",root,"<html>panel</html>",get={CompanionResource("application/json","[]".toByteArray())},command={commands+=it})
        try {
            server.open();assertEquals(200,request(server.url).first)
            assertEquals(404,request(server.url.substringBefore("/s/")+"/s/wrong").first)
            assertEquals(200,request(server.url+"/library").first)
            assertEquals(200,request(server.url+"/command","{\"action\":\"down\"}").first)
            assertEquals("down",commands.single().action)
            assertEquals(400,request(server.url+"/command","{\"action\":\"shell\"}").first)
            assertEquals(400,request(server.url+"/command","{\"action\":\"tap\",\"x\":2,\"y\":0}").first)
            assertEquals(415,request(server.url+"/command","x=y","text/plain").first)
        } finally {server.close();root.deleteRecursively()}
    }
    @Test fun rejectsApkAndRemovesUploadTemporaryAfterImport() {
        val root=Files.createTempDirectory("companion").toFile();var imported=""
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("text/plain",byteArrayOf())},command={},received={file,name->assertTrue(file.exists());imported=name})
        try {
            server.open()
            assertEquals(400,request(server.url+"/upload?name=app.apk","fake","application/octet-stream").first)
            val response=request(server.url+"/upload?name=book.cbz","pages","application/octet-stream")
            assertEquals(response.second,200,response.first)
            assertEquals("book.cbz",imported);assertEquals(0,root.listFiles()!!.size)
        } finally {server.close();root.deleteRecursively()}
    }
    @Test fun expiredSessionCannotReadOrControl() {
        val root=Files.createTempDirectory("companion").toFile();var now=1L
        val server=CompanionServer("127.0.0.1",root,"panel",get={error("must not run")},command={error("must not run")},clock={now},lifetimeMillis=100)
        try {server.open();now=102;assertEquals(410,request(server.url).first)}finally{server.close();root.deleteRecursively()}
    }
    @Test fun closedSessionCannotBeOpenedAgain() {
        val root=Files.createTempDirectory("companion").toFile()
        val server=CompanionServer("127.0.0.1",root,"panel",get={error("unused")},command={})
        try {server.close();assertThrows(IllegalStateException::class.java){server.open()}}finally{server.close();root.deleteRecursively()}
    }
    @Test fun rejectsForeignOriginHostOversizeAndDisguisedApk() {
        val root=Files.createTempDirectory("companion").toFile()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("text/plain",byteArrayOf())},command={},received={_,_->error("must not import APK")})
        try {
            server.open();val url=URL(server.url)
            fun raw(extra:String,host:String="${url.host}:${url.port}"):String=java.net.Socket(url.host,url.port).use {socket->
                socket.soTimeout=3000
                socket.getOutputStream().write("GET ${url.path}/library HTTP/1.1\r\nHost: $host\r\n$extra\r\n".toByteArray())
                socket.getInputStream().bufferedReader().readLine()
            }
            assertTrue(raw("Origin: https://example.invalid\r\n").contains("403"))
            assertTrue(raw("","other.invalid").contains("403"))
            assertEquals(400,request(server.url+"/command","x".repeat(9000)).first)
            val apk=java.io.ByteArrayOutputStream().also {bytes->java.util.zip.ZipOutputStream(bytes).use {z->z.putNextEntry(java.util.zip.ZipEntry("AndroidManifest.xml"));z.write(byteArrayOf(1));z.closeEntry()}}.toByteArray()
            val c=URL(server.url+"/upload?name=disguised.cbz").openConnection() as HttpURLConnection
            try {c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/octet-stream");c.setFixedLengthStreamingMode(apk.size);c.outputStream.use{it.write(apk)};assertEquals(400,c.responseCode)}finally{c.disconnect()}
            assertEquals(0,root.listFiles()!!.size)
        } finally {server.close();root.deleteRecursively()}
    }
}
