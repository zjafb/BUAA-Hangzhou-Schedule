package cn.edu.buaa.hzcampus.api.plantform

internal expect object PlatformAesCfbNoPadding {
  fun encrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray

  fun decrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
}
