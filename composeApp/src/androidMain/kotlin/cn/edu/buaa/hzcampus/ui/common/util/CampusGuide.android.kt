package cn.edu.buaa.hzcampus.ui.common.util

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 「北航中法未来科技学院（杭州）学习生活自助指南」静态站点。 */
private const val CAMPUS_GUIDE_URL = "https://noct-buaa.github.io/Self-Help-Guide-of-BIFAST-BUAA/"

/** 应用内 WebView 打开校园指南（纯静态站点，无需钉钉等外部容器）。 */
@Composable
actual fun rememberOpenCampusGuide(): () -> Unit {
  var showWebView by remember { mutableStateOf(false) }
  var webView by remember { mutableStateOf<WebView?>(null) }

  fun handleBack() {
    val view = webView
    if (view != null && view.canGoBack()) {
      view.goBack()
    } else {
      showWebView = false
    }
  }

  if (showWebView) {
    Dialog(
        onDismissRequest = { handleBack() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                text = "校园指南",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
            Box(modifier = Modifier.weight(1f))
            TextButton(onClick = { webView?.reload() }) { Text("刷新") }
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
                  // 站内链接留在本 WebView 内，不跳出应用。
                  webViewClient = WebViewClient()
                  webChromeClient = WebChromeClient()
                  loadUrl(CAMPUS_GUIDE_URL)
                  webView = this
                }
              },
          )
        }
      }
    }
  }

  return remember { { showWebView = true } }
}
