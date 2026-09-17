package cn.edu.buaa.hzcampus.ui.common.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 杭州国际校园「空间预约管理平台」门户地址。 */
private const val SPACE_RESERVATION_URL = "https://hzsm.buaa.edu.cn/venue/portal"

/**
 * 打开「空间预约管理平台」：直接交给系统浏览器打开门户地址。
 *
 * 该门户依赖钉钉容器里的 JSAPI，在应用内 WebView 中打开只会白屏（已实测），因此这里不再做应用内嵌页面， 统一用 `ACTION_VIEW`
 * 跳出到浏览器；若设备已装钉钉并配置了链接处理，系统也会把它交给钉钉。
 */
@Composable
actual fun rememberOpenDingTalkSpaceReservation(): () -> Unit {
  val context = LocalContext.current
  return remember(context) { { runCatching { openInBrowser(context, SPACE_RESERVATION_URL) } } }
}

private fun openInBrowser(context: Context, url: String) {
  val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 明确标记为浏览器打开，避免被其它 App 抢走。
        putExtra("com.android.browser.application_id", context.packageName)
      }
  context.startActivity(intent)
}
