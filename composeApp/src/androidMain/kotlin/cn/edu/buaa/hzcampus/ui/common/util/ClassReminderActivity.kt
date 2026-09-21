package cn.edu.buaa.hzcampus.ui.common.util

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 课程与今日计划共用的提醒页，通过系统通知全屏意图进入。 */
class ClassReminderActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= 27) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
          WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
              WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
      )
    }
    showReminder()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    showReminder()
  }

  private fun showReminder() {
    val title =
        intent.getStringExtra("title")
            ?: run {
              finish()
              return
            }
    val place = intent.getStringExtra("place").orEmpty()
    val time = intent.getStringExtra("time").orEmpty()
    val id = intent.getIntExtra("notificationId", 2901)
    val heading = intent.getStringExtra("heading") ?: "课前提醒"
    val note = intent.getStringExtra("note").orEmpty()
    val expiresAt = intent.getLongExtra("expiresAt", Long.MAX_VALUE)
    if (System.currentTimeMillis() >= expiresAt) {
      finish()
      return
    }
    setContent {
      MaterialTheme {
        LaunchedEffect(id) {
          delay(minOf(120_000, (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)))
          finish()
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
          Column(
              Modifier.fillMaxSize()
                  .safeDrawingPadding()
                  .verticalScroll(rememberScrollState())
                  .padding(28.dp),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(heading, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(28.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (time.isNotBlank()) {
              Spacer(Modifier.height(16.dp))
              Text(time, style = MaterialTheme.typography.titleLarge)
            }
            if (place.isNotBlank()) {
              Spacer(Modifier.height(12.dp))
              Text("上课地点：$place", style = MaterialTheme.typography.titleMedium)
            }
            if (note.isNotBlank()) {
              Spacer(Modifier.height(12.dp))
              Text(note, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = {
                  getSystemService(NotificationManager::class.java).cancel(id)
                  finish()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
              Text("知道了")
            }
            TextButton(
                onClick = {
                  getSystemService(NotificationManager::class.java).cancel(id)
                  packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
                  finish()
                }
            ) {
              Text("打开课表应用")
            }
          }
        }
      }
    }
  }
}
