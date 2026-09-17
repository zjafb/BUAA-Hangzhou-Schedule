package cn.edu.buaa.hzcampus.ui.common.util

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 杭州国际校园「空间预约管理平台」门户地址。 */
private const val SPACE_RESERVATION_URL = "https://hzsm.buaa.edu.cn/venue/portal"

/** 打开「空间预约管理平台」：优先在应用内用 WebView 直接加载门户，不跳出 App。 若门户要求钉钉容器环境，页面顶部提供「用钉钉打开」兜底。 */
@Composable
actual fun rememberOpenDingTalkSpaceReservation(): () -> Unit {
  val context = LocalContext.current
  var showWebView by remember { mutableStateOf(false) }

  fun openInDingTalk() {
    val dingTalkLink =
        "dingtalk://dingtalkclient/page/link?url=" +
            Uri.encode(SPACE_RESERVATION_URL) +
            "&pc_slide=true"
    val dingTalkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(dingTalkLink))
    if (dingTalkIntent.resolveActivity(context.packageManager) != null) {
      context.startActivity(dingTalkIntent)
    } else {
      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SPACE_RESERVATION_URL)))
    }
  }

  if (showWebView) {
    Dialog(
        onDismissRequest = { showWebView = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                text = "空间预约",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
            Box(modifier = Modifier.weight(1f))
            TextButton(onClick = { openInDingTalk() }) { Text("用钉钉打开") }
            TextButton(onClick = { showWebView = false }) { Text("关闭") }
          }
          AndroidView(
              modifier = Modifier.fillMaxSize(),
              factory = { ctx ->
                WebView(ctx).apply {
                  settings.javaScriptEnabled = true
                  settings.domStorageEnabled = true
                  settings.useWideViewPort = true
                  settings.loadWithOverviewMode = true
                  webViewClient = WebViewClient()
                  webChromeClient = WebChromeClient()
                  loadUrl(SPACE_RESERVATION_URL)
                }
              },
          )
        }
      }
    }
  }

  return remember { { showWebView = true } }
}
