package cn.edu.buaa.hzcampus.api.plantform

internal actual object PlatformRsaPkcs1Encrypt {
  actual fun encrypt(input: ByteArray, publicKeyDer: ByteArray): ByteArray =
      error("Local BYKC RSA support is unavailable on JS")
}
