package cn.edu.buaa.hzcampus.api.storage

import cn.edu.buaa.hzcampus.model.dto.MailAccount
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/** 邮箱账号与未读数缓存存储。 */
object MailAccountsStore {
  private const val KEY_ACCOUNTS = "mail_accounts"
  private const val KEY_UNREAD_CACHE = "mail_unread_cache"
  private val json = Json { ignoreUnknownKeys = true }

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  fun list(): List<MailAccount> {
    val raw = settings.getStringOrNull(KEY_ACCOUNTS) ?: return emptyList()
    return runCatching { json.decodeFromString<List<MailAccount>>(raw) }.getOrDefault(emptyList())
  }

  fun upsert(account: MailAccount) {
    val accounts = list().filterNot { it.id == account.id } + account
    settings.putString(KEY_ACCOUNTS, json.encodeToString(accounts))
  }

  fun delete(id: String) {
    settings.putString(KEY_ACCOUNTS, json.encodeToString(list().filterNot { it.id == id }))
  }

  fun lastUnreadCount(): Int = settings.getInt(KEY_UNREAD_CACHE, 0)

  fun pageSize(): Int = settings.getInt("mail_page_size", 20).coerceIn(1, 500)

  fun setPageSize(value: Int) {
    require(value in 1..500)
    settings.putInt("mail_page_size", value)
  }

  fun saveLastUnreadCount(count: Int) {
    settings.putInt(KEY_UNREAD_CACHE, count)
  }
}
