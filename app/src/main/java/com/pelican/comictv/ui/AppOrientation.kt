package com.pelican.comictv.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Names are persisted. Degrees turn the app clockwise, for a projector mounted on its side. */
enum class AppOrientation(val label: String, val degrees: Float) {
    Landscape("Paisagem", 0f), Portrait90("Retrato 90°", 90f), Portrait270("Retrato 270°", 270f);

    val portrait get() = this != Landscape
    fun next() = entries[(ordinal + 1) % entries.size]

    /** Maps a normalized point of what the viewer sees to the normalized point of the physical window. */
    fun windowPoint(x: Float, y: Float): Pair<Float, Float> = when (this) {
        Landscape -> x to y
        Portrait90 -> (1f - y) to x
        Portrait270 -> y to (1f - x)
    }

    companion object {
        fun restore(name: String?) = entries.firstOrNull { it.name == name } ?: Landscape
    }
}

/** A per-device choice: it follows how this projector is mounted, so it stays out of the portable backup. */
class AppOrientationPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(AppOrientation.restore(preferences.getString("app-orientation", null)))
    val state = _state.asStateFlow()
    fun update(orientation: AppOrientation) {
        preferences.edit().putString("app-orientation", orientation.name).apply()
        _state.value = orientation
    }
    fun cycle() = update(_state.value.next())
}

internal val LocalAppOrientation = staticCompositionLocalOf { AppOrientation.Landscape }

/**
 * Turns its child as a real View, with swapped bounds. Compose keeps measuring and searching focus in
 * the viewer's coordinates, so the arrows follow the rotated layout and no key needs remapping.
 */
class OrientedRootLayout(context: Context) : FrameLayout(context) {
    var degrees = 0f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    init { clipChildren = false }

    private val swapped get() = degrees % 180f != 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val childWidth = MeasureSpec.makeMeasureSpec(if (swapped) height else width, MeasureSpec.EXACTLY)
        val childHeight = MeasureSpec.makeMeasureSpec(if (swapped) width else height, MeasureSpec.EXACTLY)
        for (index in 0 until childCount) getChildAt(index).measure(childWidth, childHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val x = (width - child.measuredWidth) / 2
            val y = (height - child.measuredHeight) / 2
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            child.rotation = degrees
        }
    }
}

private class OrientedDialogRoot(context: Context, override val window: Window) : AbstractComposeView(context), DialogWindowProvider {
    private var body by mutableStateOf<@Composable () -> Unit>({})
    override var shouldCreateCompositionOnAttachedToWindow = false
        private set
    fun show(parent: CompositionContext, content: @Composable () -> Unit) {
        setParentCompositionContext(parent)
        body = content
        shouldCreateCompositionOnAttachedToWindow = true
        createComposition()
    }
    @Composable override fun Content() { body() }
}

/** Dialog windows do not inherit the activity's View rotation, so each one turns its own root. */
private class OrientedDialogWindow(owner: View, onDismiss: () -> Unit, dismissOnBack: Boolean) :
    ComponentDialog(owner.context, android.R.style.Theme_Translucent_NoTitleBar) {
    private val frame = OrientedRootLayout(context)
    private val root = OrientedDialogRoot(context, window!!)

    init {
        root.setViewTreeLifecycleOwner(owner.findViewTreeLifecycleOwner())
        root.setViewTreeViewModelStoreOwner(owner.findViewTreeViewModelStoreOwner())
        root.setViewTreeSavedStateRegistryOwner(owner.findViewTreeSavedStateRegistryOwner())
        root.setViewTreeOnBackPressedDispatcherOwner(this)
        frame.addView(root)
        setContentView(frame)
        window!!.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.6f)
        }
        setCanceledOnTouchOutside(false)
        onBackPressedDispatcher.addCallback(this) { if (dismissOnBack) onDismiss() }
    }

    var degrees: Float
        get() = frame.degrees
        set(value) { frame.degrees = value }

    fun setBody(parent: CompositionContext, content: @Composable () -> Unit) = root.show(parent, content)
    fun disposeBody() = root.disposeComposition()
}

/** The app's Dialog: the platform window in landscape, and a window turned with the app in portrait. */
@Composable internal fun ComicDialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit) {
    val orientation = LocalAppOrientation.current
    if (!orientation.portrait) {
        Dialog(onDismissRequest, properties, content)
        return
    }
    val view = LocalView.current
    val parent = rememberCompositionContext()
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestContent by rememberUpdatedState(content)
    val dialog = remember(view, parent) {
        OrientedDialogWindow(view, { latestDismiss() }, properties.dismissOnBackPress).also {
            it.setBody(parent) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (properties.usePlatformDefaultWidth) Box(Modifier.fillMaxWidth(.92f), contentAlignment = Alignment.Center) { latestContent() }
                    else latestContent()
                }
            }
        }
    }
    SideEffect { dialog.degrees = orientation.degrees }
    DisposableEffect(dialog) {
        dialog.degrees = orientation.degrees
        dialog.show()
        onDispose { dialog.dismiss(); dialog.disposeBody() }
    }
}
