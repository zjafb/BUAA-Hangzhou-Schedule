package cn.edu.buaa.hzcampus.ui.common.util

import android.content.Context

/** 持有应用 Context，供非 Composable 的 actual 实现使用。 */
object AppContextHolder {
  var context: Context? = null
}
