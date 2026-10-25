package com.pelican.comictv.ui

import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect

/** State-based fields honor showKeyboardOnFocus; the legacy String overload does not. */
@Composable internal fun TvTextInput(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle(color = Color.White, fontSize = 18.sp),
    cursorBrush: Brush = SolidColor(TvColors.Amber),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    password: Boolean = false, onSubmit: () -> Unit = {}, maxLength: Int? = null,
) {
    // Persistence belongs to the caller; do not add access keys to saved instance state.
    val state = remember { TextFieldState(initialText = value) }
    val currentValue by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val keyboard = LocalSoftwareKeyboardController.current
    var activated by remember { mutableStateOf(false) }
    var activation by remember { mutableIntStateOf(0) }
    LaunchedEffect(activation) {
        if (activation > 0) { withFrameNanos { }; if (activated) keyboard?.show() }
    }
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text -> if (text != currentValue) change(text) }
    }
    val inputModifier = modifier.onFocusChanged { if (!it.hasFocus) activated = false }.onPreviewKeyEvent { event ->
        if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
            if (event.type == KeyEventType.KeyUp) { activated = true; activation++ }
            true // Consume both halves of OK; opening the keyboard must not submit the field.
        } else false
    }
    // Enabling this only after OK also starts the platform input session. show() alone cannot
    // open an IME for a state-based field whose input session has not been started yet.
    val options = keyboardOptions.copy(showKeyboardOnFocus = activated)
    val limitInput = maxLength?.let { limit -> InputTransformation {
        if (length > limit) replace(limit, length, "")
    } }
    if (password) BasicSecureTextField(state = state, modifier = inputModifier,
        inputTransformation = limitInput,
        textStyle = textStyle, cursorBrush = cursorBrush, keyboardOptions = options,
        textObfuscationMode = TextObfuscationMode.Hidden,
        onKeyboardAction = { keyboard?.hide(); onSubmit() })
    else BasicTextField(state = state, modifier = inputModifier,
        inputTransformation = limitInput,
        textStyle = textStyle, cursorBrush = cursorBrush, keyboardOptions = options,
        lineLimits = TextFieldLineLimits.SingleLine,
        onKeyboardAction = { keyboard?.hide(); onSubmit() })
}
