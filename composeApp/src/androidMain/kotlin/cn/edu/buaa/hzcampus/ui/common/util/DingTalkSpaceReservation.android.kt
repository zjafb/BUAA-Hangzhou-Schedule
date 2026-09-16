package cn.edu.buaa.hzcampus.ui.common.util

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 杭州国际校园「空间预约管理平台」门户地址。 */
private const val SPACE_RESERVATION_URL = "https://hzsm.buaa.edu.cn/venue/portal"

@Composable
actual fun rememberOpenDingTalkSpaceReservation(): () -> Unit {
  val context = LocalContext.current
  return remember {
    {
      // 空间预约须在钉钉内完成：优先用钉钉内置浏览器打开该门户。
      val dingTalkLink =
          "dingtalk://dingtalkclient/page/link?url=" +
              Uri.encode(SPACE_RESERVATION_URL) +
              "&pc_slide=true"
      val dingTalkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(dingTalkLink))
      if (dingTalkIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(dingTalkIntent)
      } else {
        // 兜底：钉钉未安装，直接用系统浏览器打开门户。
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SPACE_RESERVATION_URL)))
      }
    }
  }
}
