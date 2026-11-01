package com.pelican.comictv.transfer

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CompanionServerTest {
    /** The server is an allowlist: a phone button whose action is missing here fails as "Comando inválido". */
    @Test fun rotateBrowserArrowsAndPinchReachTheApp() {
        val root=Files.createTempDirectory("companion-actions").toFile();val commands=mutableListOf<CompanionCommand>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{}".toByteArray())},command={commands+=it})
        try {
            server.open()
            for(body in listOf("""{"action":"rotate"}""","""{"action":"browser-up"}""","""{"action":"browser-down"}""","""{"action":"browser-left"}""","""{"action":"browser-right"}""","""{"action":"browser-ok"}""","""{"action":"reader-pinch","zoom":2,"x":0.5,"y":0.25,"dx":-0.1,"dy":0.1}"""))assertEquals(body,200,request(server.url+"/command",body).first)
            assertEquals(7,commands.size)
            for(body in listOf("""{"action":"reader-pinch","zoom":3.1,"x":0.5,"y":0.5}""","""{"action":"reader-pinch","zoom":2,"x":1.5,"y":0.5}""","""{"action":"reader-pinch","zoom":2,"x":0.5,"y":0.5,"dx":2}"""))assertEquals(body,400,request(server.url+"/command",body).first)
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
    @Test fun contextualReaderAndPointerCommandsAreBoundedAndMirrorIsAbsent() {
        val root=Files.createTempDirectory("companion-reader").toFile();val commands=mutableListOf<CompanionCommand>()
        val server=CompanionServer("127.0.0.1",root,"panel",get={CompanionResource("application/json","{\"active\":false}".toByteArray())},command={commands+=it})
        try {
            server.open()
            assertEquals(200,request(server.url+"/reader-state").first)
            assertEquals(404,request(server.url+"/screen").first)
            for(body in listOf("""{"action":"reader-pan","x":-0.12,"y":1}""","""{"action":"pointer","dx":-1,"dy":0.5}""","""{"action":"reader-page","page":4999}""","""{"action":"reader-turn","delta":-1}""","""{"action":"reader-zoom","zoom":2.25}"""))assertEquals(body,200,request(server.url+"/command",body).first)
            for(body in listOf("""{"action":"browser-pointer","dx":0,"dy":0.1}""","""{"action":"home"}""","""{"action":"settings","readerSensitivity":2,"pointerSensitivity":0.25}""","""{"action":"browser-scroll","dx":0,"dy":0.2}""","""{"action":"browser-zoom","zoom":1.5}""","""{"action":"browser-press"}""","""{"action":"browser-release"}""","""{"action":"browser-context"}"""))assertEquals(body,200,request(server.url+"/command",body).first)
            assertEquals(13,commands.size)
            for(body in listOf("""{"action":"cursor","cursorSize":50,"cursorStyle":0}""","""{"action":"cursor","cursorSize":300,"cursorStyle":2}"""))assertEquals(body,200,request(server.url+"/command",body).first)
            assertEquals(15,commands.size)
            for(body in listOf("""{"action":"cursor","cursorSize":49,"cursorStyle":0}""","""{"action":"cursor","cursorSize":301}""","""{"action":"cursor","cursorStyle":3}""","""{"action":"cursor","cursorSize":100,"cursorStyle":-1}"""))assertEquals(body,400,request(server.url+"/command",body).first)
            for(body in listOf("""{"action":"settings","readerSensitivity":0}""","""{"action":"settings","zoomSensitivity":4}""","""{"action":"browser-scroll","dy":2}""","""{"action":"browser-zoom","zoom":2.1}"""))assertEquals(body,400,request(server.url+"/command",body).first)

            for(body in listOf("""{"action":"reader-pan","x":-1.1}""","""{"action":"pointer","dx":2}""","""{"action":"reader-page","page":-1}""","""{"action":"reader-page","page":5000}""","""{"action":"reader-turn","delta":2}""","""{"action":"reader-zoom","zoom":0.9}""","""{"action":"reader-zoom","zoom":3.1}"""))assertEquals(body,400,request(server.url+"/command",body).first)
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
