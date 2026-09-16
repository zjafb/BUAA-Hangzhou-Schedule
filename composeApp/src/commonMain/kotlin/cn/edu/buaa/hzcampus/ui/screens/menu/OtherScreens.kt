package cn.edu.buaa.hzcampus.ui.screens.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.buaa.hzcampus.AppInfo
import cn.edu.buaa.hzcampus.model.dto.UserInfo

@Composable
fun MyScreen(userInfo: UserInfo?, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
    // Text(
    //         text = "我的",
    //         style = MaterialTheme.typography.headlineMedium,
    //         fontWeight = FontWeight.Bold,
    //         modifier = Modifier.padding(bottom = 16.dp)
    // )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
      Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalAlignment = Alignment.Start,
      ) {
        if (userInfo != null) {
          UserInfoItem("姓名", userInfo.name ?: "未知")
          UserInfoItem("学号", userInfo.schoolid ?: "未知")
          UserInfoItem("用户名", userInfo.username ?: "未知")
          UserInfoItem("手机号", userInfo.phone ?: "未绑定")
          UserInfoItem("邮箱", userInfo.email ?: "未绑定")
          UserInfoItem("证件类型", userInfo.idCardTypeName ?: "未知")
          UserInfoItem("证件号码", userInfo.idCardNumber ?: "未知")
        } else {
          Text(
              text = "正在加载用户信息...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun UserInfoItem(label: String, value: String) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
  }
}

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
    // Text(
    //         text = "关于",
    //         style = MaterialTheme.typography.headlineMedium,
    //         fontWeight = FontWeight.Bold,
    //         modifier = Modifier.padding(bottom = 16.dp)
    // )

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "北航杭州",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "版本：${AppInfo.version}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "北航杭州是面向杭州国际校园的课表工具。", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "主要功能：",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "• 课程表查询\n• 考试查询\n• 成绩查询\n• 空教室查询（杭州校区）\n• 空间预约（跳转钉钉）\n• 邮件查询\n• 今日计划\n• 自动评教",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "技术栈：Kotlin Multiplatform + Compose Multiplatform",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
