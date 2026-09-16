package cn.edu.buaa.hzcampus.api.storage

/** 加密敏感字符串（Android 用 Keystore AES/GCM，其他平台原样返回）。 */
expect fun encryptSecret(plain: String): String

/** 解密敏感字符串；失败返回空串。 */
expect fun decryptSecret(cipher: String): String
