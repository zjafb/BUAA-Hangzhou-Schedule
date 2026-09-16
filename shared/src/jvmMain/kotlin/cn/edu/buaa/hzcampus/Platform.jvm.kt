package cn.edu.buaa.hzcampus

class JVMPlatform : Platform {
  override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun supportsLocalConnectionModes(): Boolean = true
