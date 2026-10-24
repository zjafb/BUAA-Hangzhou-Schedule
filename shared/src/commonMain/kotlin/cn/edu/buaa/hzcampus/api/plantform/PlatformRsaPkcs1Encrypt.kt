package cn.edu.buaa.hzcampus.api.plantform

internal expect object PlatformRsaPkcs1Encrypt {
  fun encrypt(input: ByteArray, publicKeyDer: ByteArray): ByteArray
}
