package cn.edu.buaa.hzcampus.api.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "hzcampus_credential"

private fun getOrCreateKey(): SecretKey {
  val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
  (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
  val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
  generator.init(
      KeyGenParameterSpec.Builder(
              KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
          )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .build()
  )
  return generator.generateKey()
}

actual fun encryptSecret(plain: String): String =
    runCatching {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
      val iv = cipher.iv
      val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
      Base64.encodeToString(iv, Base64.NO_WRAP) +
          ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }.getOrDefault("")

actual fun decryptSecret(cipher: String): String =
    runCatching {
      val parts = cipher.split(":")
      if (parts.size != 2) return@runCatching ""
      val iv = Base64.decode(parts[0], Base64.NO_WRAP)
      val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
      val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
      decryptCipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
      String(decryptCipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrDefault("")
