package cn.edu.buaa.hzcampus.ui.common.util

import androidx.compose.runtime.Composable

/** No-op back handler for JS. */
@Composable
actual fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) {
  /* no-op */
}
