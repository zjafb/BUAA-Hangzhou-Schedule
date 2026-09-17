package cn.edu.buaa.hzcampus

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import buaa_hangzhou_schedule.composeapp.generated.resources.Res
import buaa_hangzhou_schedule.composeapp.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

fun main() = application {
  val icon = painterResource(Res.drawable.app_icon)
  Window(onCloseRequest = ::exitApplication, title = "北航杭州", icon = icon) { App() }
}
