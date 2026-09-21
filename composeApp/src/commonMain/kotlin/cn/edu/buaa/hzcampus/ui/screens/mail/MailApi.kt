package cn.edu.buaa.hzcampus.ui.screens.mail

import cn.edu.buaa.hzcampus.model.dto.MailAccount
import cn.edu.buaa.hzcampus.model.dto.MailMessage

/** 邮件收发后端（平台实现）。非 Android 平台返回空实现。 */
interface MailBackend {
  suspend fun loadPage(account: MailAccount, limit: Int, beforeUid: Long? = null): MailPage {
    val eligible =
        connectAndList(account)
            .filter { beforeUid == null || (it.uid.toLongOrNull() ?: 0) < beforeUid }
            .sortedByDescending { it.uid.toLongOrNull() ?: 0 }
    val page = eligible.take(limit)
    return MailPage(
        page,
        if (eligible.size > limit) page.lastOrNull()?.uid?.toLongOrNull() else null,
    )
  }

  suspend fun connectAndList(account: MailAccount): List<MailMessage>

  suspend fun markRead(account: MailAccount, uid: String)

  suspend fun markAllRead(account: MailAccount)

  suspend fun delete(account: MailAccount, uid: String)

  suspend fun send(account: MailAccount, to: String, subject: String, body: String)

  /** 拉取指定邮件的完整纯文本正文。 */
  suspend fun fetchBody(account: MailAccount, uid: String): String

  /** 只统计未读邮件数（轻量，不拉取正文）。 */
  suspend fun countUnread(account: MailAccount): Int
}

data class MailPage(val messages: List<MailMessage>, val nextBeforeUid: Long?)

expect fun createMailBackend(): MailBackend
