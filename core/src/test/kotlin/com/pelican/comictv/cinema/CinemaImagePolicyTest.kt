package com.pelican.comictv.cinema

import org.junit.Assert.*
import org.junit.Test

class CinemaImagePolicyTest {
    @Test fun horizontalContentBoundsRejectPortraitTinyAndOversizedImages() {
        CinemaImagePolicy.validate(1920,1080,"image/jpeg")
        CinemaImagePolicy.validate(3840,2160,"image/png")
        for ((w,h,mime) in listOf(Triple(720,1280,"image/jpeg"),Triple(640,360,"image/png"),
            Triple(1900,400,"image/jpeg"),Triple(16384,10000,"image/webp"),Triple(1920,1080,"image/gif"))) {
            assertThrows(IllegalArgumentException::class.java) { CinemaImagePolicy.validate(w,h,mime) }
        }
    }
    @Test fun displayBudgetNeverUpscalesAndBoundsDecodedPixelCount() {
        assertEquals(1280 to 720,CinemaImagePolicy.decodeSize(1280,720,true))
        assertEquals(1920 to 1080,CinemaImagePolicy.decodeSize(3840,2160,true))
        assertEquals(3840 to 2160,CinemaImagePolicy.decodeSize(3840,2160,false))
        for (low in listOf(true,false)) {
            val (w,h)=CinemaImagePolicy.decodeSize(Int.MAX_VALUE,Int.MAX_VALUE,low)
            assertTrue(w.toLong()*h <= if(low)1920L*1080 else 3840L*2160)
        }
    }
}
