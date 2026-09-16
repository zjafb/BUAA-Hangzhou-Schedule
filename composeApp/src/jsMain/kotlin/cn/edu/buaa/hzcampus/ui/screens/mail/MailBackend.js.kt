package cn.edu.buaa.hzcampus.ui.screens.mail

import cn.edu.buaa.hzcampus.model.dto.MailAccount
import cn.edu.buaa.hzcampus.model.dto.MailMessage

actual fun createMailBackend(): MailBackend = EmptyMailBackend

private object EmptyMailBackend : MailBackend {
  override suspend fun connectAndList(account: MailAccount): List<MailMessage> = emptyList()

  override suspend fun markRead(account: MailAccount, uid: String) {}

  override suspend fun markAllRead(account: MailAccount) {}

  override suspend fun delete(account: MailAccount, uid: String) {}

  override suspend fun send(account: MailAccount, to: String, subject: String, body: String) {}

  override suspend fun fetchBody(account: MailAccount, uid: String): String = ""
}
