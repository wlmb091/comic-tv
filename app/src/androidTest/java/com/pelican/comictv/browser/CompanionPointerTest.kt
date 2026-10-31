package com.pelican.comictv.browser

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompanionPointerTest {
    @get:Rule val compose=createComposeRule()
    @SuppressLint("ClickableViewAccessibility")
    private fun fixture(check:(RemoteBrowserWebView,MutableList<Int>)->Unit){
        lateinit var view:RemoteBrowserWebView
        val events=mutableListOf<Int>()
        compose.setContent { AndroidView(modifier=Modifier.fillMaxSize(),factory={context->RemoteBrowserWebView(context).also{
            view=it;it.setOnTouchListener{_,event->events+=event.actionMasked;false};it.requestFocus()
        }}) }
        compose.waitForIdle()
        try {compose.runOnIdle{assertTrue(view.hasFocus());check(view,events)}}
        finally {compose.runOnIdle{view.releasePointer();view.destroy()}}
    }
    @Test fun phoneContextEndsTouchEvenWithPhysicalDragModeEnabled()=fixture { view,events->
        view.dragMode=true
        assertTrue(view.companionGesture("browser-context",0f,0f,1f))
        assertEquals(listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_CANCEL),events)
        view.moveCompanionPointer(.01f,0f)
        assertFalse(events.contains(MotionEvent.ACTION_MOVE))
    }
    @Test fun phoneScrollCancelsPhysicalPressBeforeClearingOwnership()=fixture { view,events->
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(view.companionGesture("browser-scroll",0f,.1f,1f))
        assertTrue(events.contains(MotionEvent.ACTION_CANCEL))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(MotionEvent.ACTION_CANCEL,events.last())
    }
}
