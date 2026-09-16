package cn.edu.buaa.hzcampus.api.storage

actual fun encryptSecret(plain: String): String = plain

actual fun decryptSecret(cipher: String): String = cipher
