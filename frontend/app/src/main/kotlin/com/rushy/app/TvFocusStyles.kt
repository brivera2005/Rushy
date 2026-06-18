package com.rushy.app

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

fun Modifier.tvFocusHighlight(
    shape: Shape = RoundedCornerShape(ThemeColors.CornerRadius),
    focused: Boolean = false,
    selected: Boolean = false,
): Modifier = then(
    if (focused || selected) {
        Modifier.border(ThemeColors.FocusRingWidth, ThemeColors.FocusBorder, shape)
    } else {
        Modifier
    },
)

@Composable
fun rememberFocusState() = remember { mutableStateOf(false) }

fun Modifier.trackTvFocus(onFocused: (Boolean) -> Unit): Modifier = composed {
    onFocusChanged { onFocused(it.isFocused) }
}
