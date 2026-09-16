package cn.edu.buaa.hzcampus

interface Platform {
  val name: String
}

expect fun getPlatform(): Platform

expect fun supportsLocalConnectionModes(): Boolean
