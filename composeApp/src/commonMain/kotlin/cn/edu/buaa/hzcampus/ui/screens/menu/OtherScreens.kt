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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** 「关于」页：应用信息 + 使用说明 + 更新日志。 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
  val uriHandler = LocalUriHandler.current
  Column(
      modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
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
            text = "本项目是基于 UBAA 的杭州国际校园适配改版（原版是北京校区通用校园客户端），在此致谢原作者。",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "原版项目：https://github.com/BUAASubnet/UBAA",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.clickable { uriHandler.openUri("https://github.com/BUAASubnet/UBAA") },
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "主要功能：",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text =
                "• 课程表查询\n• 考试查询\n• 成绩查询\n• 空教室查询（杭州校区）\n• 希冀作业\n• 空间预约（跳转钉钉）\n• 邮件查询\n• 今日计划\n• 自动评教",
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

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "使用说明",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = ABOUT_USAGE_GUIDE, style = MaterialTheme.typography.bodyMedium)
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "更新日志",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = ABOUT_CHANGELOG, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

private val ABOUT_USAGE_GUIDE =
    """
    1. 登录
       使用北航统一身份认证账号（学号 + 密码）登录。

    2. 课表
       进入课表后先点「课表本地化」，把整学期保存到手机；之后打开课表秒开，左右切周不再联网加载。教务调整后重新本地化一次即可。

    3. 课前提醒
       设置 → 课前提醒，可选 10 / 15 / 20 / 30 分钟或自定义，修改后立即生效。

    4. 今日计划
       首页「今日计划」右上角 + 新建；单击计划可编辑，长按可删除。计划会按时间叠加显示在课表对应时段，并可设置提前提醒。

    5. 考试 / 成绩
       进入后按学期查看，顶部可切换学期。

    6. 空教室
       按日期、节次查询杭州校区的空闲教室。

    7. 希冀作业
       查看编程作业、提交状态与得分；列表先显示摘要，详情会自动补全。

    8. 邮件（北航内部邮箱）
       仅支持北航邮箱（@buaa.edu.cn）：服务器已内置（imap / smtp.buaa.edu.cn），填写北航邮箱地址和密码即可，需连接校园网。
       长按邮件、或点右上角「删除」进入批量删除；点开邮件后也能在详情里删除。

    9. 空间预约
       跳转钉钉工作台「空间预约管理平台」。

    10. 自动评教
        一键完成学期末评教任务。

    11. 连接模式
        设置里可在直连 / WebVPN / 服务器中转之间切换；校园网内一般选直连。
    """
        .trimIndent()

private val ABOUT_CHANGELOG =
    """
    v1.0.2
    · 修复课前提醒「提前时间」设置不生效（改完仍按旧时间提醒）
    · 修复邮箱批量删除删不掉的问题；新增长按删除与详情内删除按钮，入口文字改为「删除」
    · 修复课表本地化后仍每次联网加载——现在本地化后秒开，切周不再重新加载
    · 修复首页待办区希冀一直转圈——改为先出摘要、详情后台补全，并加超时与错误提示
    · 新增长按删除今日计划
    · 新增本页「使用说明」与「更新日志」

    v1.0.1
    · 修复启动与登录卡顿（服务器地址缺少端口）
    · 修复误提示更新到 1.8.0、误显示 UBAA 公告
    · 安全：登录密码改用系统密钥库加密存储，并关闭应用数据备份
    · 优化：邮箱不再预取正文、未读数轻量统计、网络超时减半
    · 新增邮箱密码可见性切换、时间滚轮磁吸吸附
    · 移除阳光打卡（杭州校区不适用）

    v1.0.0
    · 杭州国际校园适配首发：课表（14 节标准作息）、考试、成绩、空教室（限杭州）、空间预约（钉钉）、邮件查询、今日计划、希冀作业
    · 移除北京校区专属功能：博雅选课、图书馆座位、课程签到、研讨室预约、SPOC
    """
        .trimIndent()
