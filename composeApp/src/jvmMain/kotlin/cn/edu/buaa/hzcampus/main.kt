package cn.edu.buaa.hzcampus

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.painterResource
import ubaa.composeapp.generated.resources.Res
import ubaa.composeapp.generated.resources.app_icon

fun main() = application {
  val icon = painterResource(Res.drawable.app_icon)
  Window(onCloseRequest = ::exitApplication, title = "北航杭州", icon = icon) { App() }
}
