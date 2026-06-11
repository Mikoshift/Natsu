package io.mikoshift.natsu.ui.shell

import androidx.compose.runtime.staticCompositionLocalOf

val LocalDrawerOpen = staticCompositionLocalOf<(() -> Unit)?> { null }
