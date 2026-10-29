package com.pelican.comictv.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
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

/** App-owned pointer, drawn above the page. No JavaScript injection or accessibility service. */
internal class RemoteBrowserWebView(context: Context) : WebView(context) {
    var onToolbar: () -> Unit = {}
    var onPageInteraction: () -> Unit = {}
    var cursorEnabled = true
        set(value) { field = value; invalidate() }
    private val density = resources.displayMetrics.density
    private var cursorX = 0f
    private var cursorY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var clickDownTime = 0L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 || oldh == 0) { cursorX = w / 2f; cursorY = h / 2f }
        else { cursorX = cursorX.coerceIn(0f, w.toFloat()); cursorY = cursorY.coerceIn(0f, h.toFloat()) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!cursorEnabled || !hasFocus()) return
        // WebView's canvas uses document coordinates while this pointer belongs to the viewport.
        val x = scrollX + cursorX; val y = scrollY + cursorY
        paint.style = Paint.Style.FILL; paint.color = Color.argb(210, 15, 15, 15)
        canvas.drawCircle(x, y, 9f * density, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * density; paint.color = Color.WHITE
        canvas.drawCircle(x, y, 9f * density, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, 2f * density, paint)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP) onToolbar()
            return true
        }
        if (!cursorEnabled) return super.dispatchKeyEvent(event)
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1f to 0f
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1f to 0f
            KeyEvent.KEYCODE_DPAD_UP -> 0f to -1f
            KeyEvent.KEYCODE_DPAD_DOWN -> 0f to 1f
            else -> null
        }
        if (direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                onPageInteraction()
                val step = (if (event.repeatCount > 6) 30f else 16f) * density
                val position = moveBrowserCursor(cursorX, cursorY, direction.first * step, direction.second * step,
                    width, height, 24f * density)
                cursorX = position.x; cursorY = position.y
                scrollBy(position.scrollX, position.scrollY)
                invalidate()
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) clickDownTime = SystemClock.uptimeMillis()
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                onPageInteraction()
                val now = SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                    val touch = MotionEvent.obtain(clickDownTime, now, action, cursorX, cursorY, 0)
                    try { dispatchTouchEvent(touch) } finally { touch.recycle() }
                }
                invalidate()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
