package com.pelican.comictv.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView

internal data class CursorPosition(val x: Float, val y: Float, val scrollX: Int = 0, val scrollY: Int = 0)

/** Keep the pointer inside the visible site; overshooting its safe edge scrolls the document. */
internal fun moveBrowserCursor(x: Float, y: Float, dx: Float, dy: Float, width: Int, height: Int, inset: Float): CursorPosition {
    val right = (width - inset).coerceAtLeast(inset)
    val bottom = (height - inset).coerceAtLeast(inset)
    val nextX = x + dx; val nextY = y + dy
    return CursorPosition(nextX.coerceIn(inset, right), nextY.coerceIn(inset, bottom),
        (nextX - nextX.coerceIn(inset, right)).toInt(), (nextY - nextY.coerceIn(inset, bottom)).toInt())
}

/** Continuous, viewport-relative pointer. Press/drag/hold are owned here, not by key repeats. */
internal class RemoteBrowserWebView(context: Context) : WebView(context) {
    var onToolbar: () -> Unit = {}
    var onPageInteraction: () -> Unit = {}
    var onContextLink: (String?) -> Unit = {}
    var cursorEnabled = true
        set(value) { if (field != value) releasePointer(); field = value; invalidate() }
    var cursorSpeed = 1
    var dragMode = false
    private val density = resources.displayMetrics.density
    private var cursorX = 0f; private var cursorY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private val held = mutableSetOf<Int>()
    private var heldSince = 0L; private var frameAt = 0L; private var lastMove = 0L
    private var pressed = false; private var clickDownTime = 0L
    private var lastTouchX = 0f; private var lastTouchY = 0f
    private val hide = Runnable { invalidate() }
    private val hold = Runnable {
        if (pressed && !dragMode) {
            touch(MotionEvent.ACTION_CANCEL); pressed = false
            requestContextLink()
        }
    }
    private fun requestContextLink() {
        val message=android.os.Message.obtain(Handler(Looper.getMainLooper()){reply->
            onContextLink(reply.data.getString("url")?:hitTestResult?.extra);true
        })
        requestFocusNodeHref(message)
    }
    private val frame = object : Runnable {
        override fun run() {
            if (held.isEmpty() || !hasFocus() || !cursorEnabled || !isAttachedToWindow) return
            val now = SystemClock.uptimeMillis(); val dt = (now - frameAt).coerceIn(1, 32) / 1000f; frameAt = now
            val acceleration = ((now - heldSince) / 600f).coerceIn(0f, 1f)
            val speed = (140f + 420f * acceleration) * listOf(.65f, 1f, 1.45f)[cursorSpeed.coerceIn(0, 2)] * density
            val dx = (if (KeyEvent.KEYCODE_DPAD_RIGHT in held) 1 else 0) - (if (KeyEvent.KEYCODE_DPAD_LEFT in held) 1 else 0)
            val dy = (if (KeyEvent.KEYCODE_DPAD_DOWN in held) 1 else 0) - (if (KeyEvent.KEYCODE_DPAD_UP in held) 1 else 0)
            move(dx * speed * dt, dy * speed * dt)
            postOnAnimation(this)
        }
    }
    init { isFocusable = true; isFocusableInTouchMode = true; isLongClickable = false; setOnLongClickListener { true } }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 || oldh == 0) { cursorX = w / 2f; cursorY = h / 2f }
        else { cursorX = cursorX.coerceIn(0f, w.toFloat()); cursorY = cursorY.coerceIn(0f, h.toFloat()) }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!cursorEnabled || !hasFocus() || SystemClock.uptimeMillis() - lastMove > 8000) return
        val x = scrollX + cursorX; val y = scrollY + cursorY; val radius = (if (pressed) 6f else 9f) * density
        paint.style = Paint.Style.FILL; paint.color = Color.argb(220, 15, 15, 15); canvas.drawCircle(x, y, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * density; paint.color = Color.WHITE; canvas.drawCircle(x, y, radius, paint)
        paint.style = Paint.Style.FILL; canvas.drawCircle(x, y, 2f * density, paint)
    }
    private fun showPointer() { lastMove = SystemClock.uptimeMillis(); handler.removeCallbacks(hide); handler.postDelayed(hide, 8050); invalidate() }
    private fun touch(action: Int) {
        val event = MotionEvent.obtain(clickDownTime, SystemClock.uptimeMillis(), action, cursorX, cursorY, 0)
        try { dispatchTouchEvent(event) } finally { event.recycle() }
    }
    /** Relative phone input shares the existing cursor, drag, edge scroll and focus ownership. */
    fun moveCompanionPointer(dx:Float,dy:Float):Boolean {
        if(!cursorEnabled||!hasFocus()||!isAttachedToWindow||width<=0||height<=0)return false
        require(dx.isFinite()&&dy.isFinite())
        onPageInteraction()
        move(dx.coerceIn(-1f,1f)*width,dy.coerceIn(-1f,1f)*height)
        if(companionDragging){handler.removeCallbacks(companionTimeout);handler.postDelayed(companionTimeout,1200)}
        return true
    }
    private var companionDragging=false
    private val companionTimeout=Runnable { if(companionDragging)endCompanionDrag(true) }
    private fun endCompanionDrag(cancel:Boolean) {
        handler.removeCallbacks(companionTimeout);handler.removeCallbacks(hold)
        if(pressed)touch(if(cancel)MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP)
        companionDragging=false;pressed=false;showPointer()
    }
    /** Explicit phone gestures require the focused page. A dropped connection never leaves DOWN held. */
    fun companionGesture(action:String,dx:Float,dy:Float,zoom:Float):Boolean {
        // Phone arrows and OK belong to the page's own input, not to the touchpad's cursor.
        val pageKey=when(action){"browser-up"->KeyEvent.KEYCODE_DPAD_UP;"browser-down"->KeyEvent.KEYCODE_DPAD_DOWN;"browser-left"->KeyEvent.KEYCODE_DPAD_LEFT;"browser-right"->KeyEvent.KEYCODE_DPAD_RIGHT;"browser-ok"->KeyEvent.KEYCODE_ENTER;else->null}
        if(pageKey!=null){
            if(!hasFocus()||!isAttachedToWindow)return false
            onPageInteraction();endCompanionDrag(true)
            super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,pageKey));super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,pageKey))
            return true
        }
        if(!hasFocus()||!isAttachedToWindow||!cursorEnabled)return false
        onPageInteraction()
        when(action){
            "browser-pointer"->{if(!moveCompanionPointer(dx,dy))return false}
            "browser-scroll"->{endCompanionDrag(true);wheel((-dx*width).toInt(),(-dy*height).toInt())}
            "browser-zoom"->{endCompanionDrag(true);zoomBy(zoom.coerceIn(.5f,2f))}
            "browser-press"->{
                if(!companionDragging){endCompanionDrag(true);clickDownTime=SystemClock.uptimeMillis();pressed=true;companionDragging=true;touch(MotionEvent.ACTION_DOWN)}
                handler.removeCallbacks(companionTimeout);handler.postDelayed(companionTimeout,1200)
            }
            "browser-release"->endCompanionDrag(false)
            "browser-cancel"->endCompanionDrag(true)
            "browser-click","browser-context"->{
                endCompanionDrag(true);clickDownTime=SystemClock.uptimeMillis();pressed=true;touch(MotionEvent.ACTION_DOWN)
                if(action=="browser-context"){touch(MotionEvent.ACTION_CANCEL);pressed=false;requestContextLink()} else {touch(MotionEvent.ACTION_UP);pressed=false}
            }
            else->return false
        };showPointer();return true
    }
    private fun move(dx: Float, dy: Float) {
        val next = moveBrowserCursor(cursorX, cursorY, dx, dy, width, height, 12f * density)
        cursorX = next.x; cursorY = next.y; showPointer()
        if (pressed) {
            if (kotlin.math.abs(cursorX - lastTouchX) + kotlin.math.abs(cursorY - lastTouchY) > 8 * density) handler.removeCallbacks(hold)
            touch(MotionEvent.ACTION_MOVE)
        } else if (next.scrollX != 0 || next.scrollY != 0) wheel(next.scrollX, next.scrollY)
    }
    private fun wheel(dx: Int, dy: Int) {
        val property = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val coords = MotionEvent.PointerCoords().apply {
            x = cursorX; y = cursorY
            setAxisValue(MotionEvent.AXIS_VSCROLL, -dy / (24f * density))
            setAxisValue(MotionEvent.AXIS_HSCROLL, -dx / (24f * density))
        }
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(property), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        try { if (!super.dispatchGenericMotionEvent(event)) scrollBy(dx, dy) } finally { event.recycle() }
    }
    fun releasePointer() {
        handler.removeCallbacks(companionTimeout);companionDragging=false
        held.clear(); removeCallbacks(frame); handler.removeCallbacks(hold); handler.removeCallbacks(hide)
        if (pressed) touch(MotionEvent.ACTION_CANCEL)
        pressed = false; invalidate()
    }
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (isAttachedToWindow) { if (!gainFocus) releasePointer() else showPointer() }
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (!hasWindowFocus) releasePointer() }
    override fun onDetachedFromWindow() { releasePointer(); super.onDetachedFromWindow() }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(event.action==KeyEvent.ACTION_DOWN&&event.repeatCount==0&&companionDragging)endCompanionDrag(true)
        if (event.keyCode in setOf(KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_ESCAPE)) {
            if (event.action == KeyEvent.ACTION_UP) { releasePointer(); onToolbar() }; return true
        }
        if (!cursorEnabled) return super.dispatchKeyEvent(event)
        if (event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                onPageInteraction(); val first = held.isEmpty(); held.add(event.keyCode)
                if (first) { heldSince = SystemClock.uptimeMillis(); frameAt = heldSince; postOnAnimation(frame) }
                move(when(event.keyCode) { KeyEvent.KEYCODE_DPAD_LEFT -> -12f * density; KeyEvent.KEYCODE_DPAD_RIGHT -> 12f * density; else -> 0f },
                    when(event.keyCode) { KeyEvent.KEYCODE_DPAD_UP -> -12f * density; KeyEvent.KEYCODE_DPAD_DOWN -> 12f * density; else -> 0f })
            } else if (event.action == KeyEvent.ACTION_UP) { held.remove(event.keyCode); if (held.isEmpty()) removeCallbacks(frame) }
            return true
        }
        if (event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                onPageInteraction(); clickDownTime = SystemClock.uptimeMillis(); pressed = true
                lastTouchX = cursorX; lastTouchY = cursorY; touch(MotionEvent.ACTION_DOWN); showPointer()
                if (!dragMode) handler.postDelayed(hold, 650)
            } else if (event.action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(hold)
                if (pressed) touch(if (event.isCanceled) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP)
                pressed = false; showPointer()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
