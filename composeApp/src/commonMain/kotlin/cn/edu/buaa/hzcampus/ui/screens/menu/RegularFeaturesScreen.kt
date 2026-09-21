package cn.edu.buaa.hzcampus.ui.screens.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class FeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

@Composable
fun RegularFeaturesScreen(
    onScheduleClick: () -> Unit,
    onExamClick: () -> Unit,
    onCourseQueryClick: () -> Unit,
    onClassroomClick: () -> Unit,
    onJudgeClick: () -> Unit,
    onSpaceReservationClick: () -> Unit,
    onMailClick: () -> Unit,
    onCampusGuideClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val features =
      listOf(
          FeatureItem(
              id = "schedule",
              title = "课表查询",
              description = "查看课程表，支持周视图和学期切换",
              icon = Icons.Default.CalendarToday,
          ),
          FeatureItem(
              id = "exam",
              title = "考试查询",
              description = "查看考试安排，支持学期切换",
              icon = Icons.AutoMirrored.Filled.Assignment,
          ),
          FeatureItem(
              id = "grade",
              title = "课程查询",
              description = "逐门查看成绩、学分与学时明细",
              icon = Icons.Default.Grade,
          ),
          FeatureItem(
              id = "classroom",
              title = "空教室查询",
              description = "查询杭州校区空闲教室",
              icon = Icons.Default.MeetingRoom,
          ),
          FeatureItem(
              id = "judge",
              title = "希冀作业",
              description = "查看希冀平台的编程作业与提交进度",
              icon = Icons.Default.Code,
          ),
          FeatureItem(
              id = "space",
              title = "空间预约",
              description = "在浏览器中打开空间预约管理平台",
              icon = Icons.Default.DateRange,
          ),
          FeatureItem(
              id = "mail",
              title = "邮件查询",
              description = "绑定邮箱，收发与查询邮件",
              icon = Icons.Default.MailOutline,
          ),
          FeatureItem(
              id = "guide",
              title = "校园指南",
              description = "杭州校区学习生活自助指南",
              icon = Icons.AutoMirrored.Filled.MenuBook,
          ),
      )

  FeatureGrid(modifier = modifier) {
    items(features, key = { it.id }) { feature ->
      FeatureCard(
          feature = feature,
          onClick = {
            when (feature.id) {
              "schedule" -> onScheduleClick()
              "exam" -> onExamClick()
              "grade" -> onCourseQueryClick()
              "classroom" -> onClassroomClick()
              "judge" -> onJudgeClick()
              "space" -> onSpaceReservationClick()
              "mail" -> onMailClick()
              "guide" -> onCampusGuideClick()
            }
          },
      )
    }
  }
}

@Composable
internal fun FeatureCard(feature: FeatureItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Card(
      onClick = onClick,
      modifier = modifier.fillMaxWidth().heightIn(min = 160.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Icon(
          imageVector = feature.icon,
          contentDescription = null,
          modifier = Modifier.size(48.dp),
          tint = MaterialTheme.colorScheme.primary,
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
          text = feature.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
          text = feature.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
      )
    }
  }
}
