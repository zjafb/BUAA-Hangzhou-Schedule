package cn.edu.buaa.hzcampus

class JsPlatform : Platform {
  override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): Platform = JsPlatform()

actual fun supportsLocalConnectionModes(): Boolean = false
