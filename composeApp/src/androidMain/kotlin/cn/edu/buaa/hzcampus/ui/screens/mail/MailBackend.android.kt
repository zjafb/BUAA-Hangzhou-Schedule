package cn.edu.buaa.hzcampus.ui.screens.mail

import cn.edu.buaa.hzcampus.model.dto.MailAccount
import cn.edu.buaa.hzcampus.model.dto.MailMessage
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.UIDFolder
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Properties
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun createMailBackend(): MailBackend = AndroidMailBackend

/** 带清晰中文信息的邮件异常。 */
private class MailException(message: String, cause: Throwable? = null) : Exception(message, cause)

private object AndroidMailBackend : MailBackend {

  override suspend fun connectAndList(account: MailAccount): List<MailMessage> =
      withImapFallback(account) { folder -> listMessages(folder) }

  override suspend fun countUnread(account: MailAccount): Int =
      withImapFallback(account) { folder -> folder.getUnreadMessageCount() }

  override suspend fun markRead(account: MailAccount, uid: String): Unit =
      withImapFallback(account) { folder -> setSeen(folder, uid, true) }

  override suspend fun markAllRead(account: MailAccount): Unit =
      withImapFallback(account) { folder ->
        folder.messages.forEach { it.setFlag(Flags.Flag.SEEN, true) }
      }

  override suspend fun delete(account: MailAccount, uid: String) {
    // 先走完整的 SSL/明文回退链路，再在链路之外报告「未找到」，避免被当成连接失败重试。
    val deleted = withImapFallback(account) { folder -> deleteByUid(folder, uid) }
    if (!deleted) throw MailException("未找到该邮件（UID $uid），可能已被其他客户端删除")
  }

  override suspend fun fetchBody(account: MailAccount, uid: String): String =
      withImapFallback(account) { folder -> bodyByUid(folder, uid) }

  override suspend fun send(
      account: MailAccount,
      to: String,
      subject: String,
      body: String,
  ): Unit = withSmtpFallback(account) { ssl -> smtpSend(account, to, subject, body, ssl) }

  // ---- 连接与 SSL/STARTTLS 回退 ----

  private suspend fun <T> withImapFallback(account: MailAccount, block: (Folder) -> T): T =
      withContext(Dispatchers.IO) {
        try {
          withImapFolder(account, ssl = true, block)
        } catch (sslError: Exception) {
          try {
            withImapFolder(account, ssl = false, block)
          } catch (plainError: Exception) {
            throw MailException(plainError.message ?: sslError.message ?: "未知错误", plainError)
          }
        }
      }

  private suspend fun <T> withSmtpFallback(account: MailAccount, block: (Boolean) -> T): T =
      withContext(Dispatchers.IO) {
        try {
          block(true)
        } catch (sslError: Exception) {
          try {
            block(false)
          } catch (plainError: Exception) {
            throw MailException(plainError.message ?: sslError.message ?: "未知错误", plainError)
          }
        }
      }

  private fun <T> withImapFolder(account: MailAccount, ssl: Boolean, block: (Folder) -> T): T {
    val protocol = if (ssl) "imaps" else "imap"
    val props =
        Properties().apply {
          put("mail.store.protocol", protocol)
          put("mail.$protocol.connectiontimeout", "10000")
          put("mail.$protocol.timeout", "15000")
          if (ssl) {
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.trust", "*")
            put("mail.imaps.ssl.socketFactory", trustAllSslContext().socketFactory)
            put("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3")
          } else {
            put("mail.imap.starttls.enable", "true")
            put("mail.imap.starttls.required", "false")
          }
        }
    val session = Session.getInstance(props, null)
    return try {
      session.getStore(protocol).use { store ->
        store.connect(account.imapHost, account.imapPort, account.email, account.password)
        store.getFolder("INBOX").use { folder ->
          folder.open(Folder.READ_WRITE)
          block(folder)
        }
      }
    } catch (e: Exception) {
      throw MailException(classifyMailError(e, account.imapHost, account.imapPort), e)
    }
  }

  private fun smtpSend(
      account: MailAccount,
      to: String,
      subject: String,
      body: String,
      ssl: Boolean,
  ) {
    val protocol = if (ssl) "smtps" else "smtp"
    val props =
        Properties().apply {
          put("mail.transport.protocol", protocol)
          put("mail.$protocol.host", account.smtpHost)
          put("mail.$protocol.port", account.smtpPort.toString())
          put("mail.$protocol.auth", "true")
          put("mail.$protocol.connectiontimeout", "10000")
          put("mail.$protocol.timeout", "15000")
          if (ssl) {
            put("mail.smtps.ssl.enable", "true")
            put("mail.smtps.ssl.trust", "*")
            put("mail.smtps.ssl.socketFactory", trustAllSslContext().socketFactory)
            put("mail.smtps.ssl.protocols", "TLSv1.2 TLSv1.3")
          } else {
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "false")
          }
        }
    val session = Session.getInstance(props, null)
    val message =
        MimeMessage(session).apply {
          setFrom(InternetAddress(account.email))
          setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
          setSubject(subject)
          setText(body)
        }
    try {
      Transport.send(message, account.email, account.password)
    } catch (e: Exception) {
      throw MailException(classifyMailError(e, account.smtpHost, account.smtpPort), e)
    }
  }

  // ---- 邮件读写 ----

  private fun listMessages(folder: Folder): List<MailMessage> =
      folder.messages.map { msg ->
        MailMessage(
            uid = (folder as UIDFolder).getUID(msg).toString(),
            subject = msg.subject ?: "(无主题)",
            from =
                (msg.from?.firstOrNull() as? InternetAddress)?.address
                    ?: msg.from?.firstOrNull()?.toString().orEmpty(),
            date = msg.sentDate?.toString() ?: msg.receivedDate?.toString().orEmpty(),
            unread = !msg.flags.contains(Flags.Flag.SEEN),
            bodyPreview = "",
        )
      }

  private fun setSeen(folder: Folder, uid: String, seen: Boolean) {
    folder.messages
        .firstOrNull { (folder as UIDFolder).getUID(it).toString() == uid }
        ?.setFlag(Flags.Flag.SEEN, seen)
  }

  /**
   * 标记 \Deleted 后立即 expunge，把邮件真正从收件箱移除。
   * 优先用 UID 直接定位，避免遍历整个文件夹；返回是否真的删除了邮件。
   */
  private fun deleteByUid(folder: Folder, uid: String): Boolean {
    val uidFolder = folder as UIDFolder
    val targetUid = uid.toLongOrNull()
    val message =
        targetUid?.let { runCatching { uidFolder.getMessageByUID(it) }.getOrNull() }
            ?: folder.messages.firstOrNull { uidFolder.getUID(it).toString() == uid }
            ?: return false
    message.setFlag(Flags.Flag.DELETED, true)
    // expunge 只清理本次会话中被标记删除的邮件。
    folder.expunge()
    return true
  }

  private fun bodyByUid(folder: Folder, uid: String): String {
    val msg =
        folder.messages.firstOrNull { (folder as UIDFolder).getUID(it).toString() == uid }
            ?: return "（未找到该邮件）"
    // 打开正文时顺便标记已读，避免另开一条 IMAP 连接。
    runCatching { msg.setFlag(Flags.Flag.SEEN, true) }
    val text = runCatching { extractBodyText(msg) }.getOrDefault("").trim()
    return text.ifBlank { "（该邮件无正文内容）" }
  }

  // ---- 正文提取（按 charset 手动解码，避免乱码） ----

  private fun extractBodyText(msg: Message): String {
    val plain = StringBuilder()
    val html = StringBuilder()
    collectPartText(msg, plain, html)
    val p = plain.toString().trim()
    if (p.isNotEmpty()) return p
    val h = html.toString().trim()
    return if (h.isNotEmpty()) htmlToText(h) else ""
  }

  /** 递归遍历所有 part：text/plain 优先、text/html 次之；附件等二进制 part 跳过。 */
  private fun collectPartText(part: Part, plain: StringBuilder, html: StringBuilder) {
    val type = part.contentType?.substringBefore(";")?.trim()?.lowercase() ?: ""
    when {
      type.startsWith("multipart/") -> {
        val multipart = runCatching { part.content as? Multipart }.getOrNull() ?: return
        for (i in 0 until multipart.count) {
          runCatching { multipart.getBodyPart(i) }
              .getOrNull()
              ?.let { collectPartText(it, plain, html) }
        }
      }
      type == "text/plain" -> partText(part)?.let { plain.append(it).append('\n') }
      type == "text/html" -> partText(part)?.let { html.append(it).append('\n') }
      type.isEmpty() -> partText(part)?.let { plain.append(it).append('\n') }
    // 附件（image/*、application/* 等）跳过，不下载
    }
  }

  /** 读取 part 的原始字节并按 Content-Type 声明的 charset 解码。 */
  private fun partText(part: Part): String? {
    val bytes = runCatching { part.inputStream.use { it.readBytes() } }.getOrNull() ?: return null
    if (bytes.isEmpty()) return ""
    return decodeText(bytes, extractCharset(part.contentType))
  }

  private fun extractCharset(contentType: String?): String? {
    if (contentType == null) return null
    return Regex("(?i)charset\\s*=\\s*\"?([^\"\\s;]+)").find(contentType)?.groupValues?.get(1)
  }

  /** 依次尝试：声明的 charset → UTF-8 → GB18030/GBK/GB2312 → ISO-8859-1。 */
  private fun decodeText(bytes: ByteArray, charsetName: String?): String {
    val candidates = mutableListOf<String>()
    charsetName?.let { candidates.add(it) }
    candidates.addAll(listOf("UTF-8", "GB18030", "GBK", "GB2312", "ISO-8859-1"))
    for (name in candidates.distinct()) {
      val decoded = runCatching { String(bytes, charset(name)).removePrefix("\uFEFF") }.getOrNull()
      if (!decoded.isNullOrBlank()) return decoded
    }
    return ""
  }

  /** 将 HTML 粗略转为纯文本。 */
  private fun htmlToText(html: String): String =
      html
          .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
          .replace(Regex("(?is)<br\\s*/?>"), "\n")
          .replace(Regex("(?is)</p\\s*>"), "\n")
          .replace(Regex("<[^>]*>"), "")
          .replace("&nbsp;", " ")
          .replace("&amp;", "&")
          .replace("&lt;", "<")
          .replace("&gt;", ">")
          .replace("&quot;", "\"")
          .replace("&#39;", "'")
          .replace(Regex("\\n{3,}"), "\n\n")
          .trim()

  // ---- 错误归类 ----

  private fun causeChain(e: Throwable): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = e
    while (current != null) {
      chain.add(current)
      current = current.cause
    }
    return chain
  }

  private fun classifyMailError(e: Throwable, host: String, port: Int): String {
    val chain = causeChain(e)
    if (chain.any { it is AuthenticationFailedException }) {
      return "登录失败，请检查邮箱地址或授权码"
    }
    if (chain.any { it is SocketTimeoutException }) {
      return "连接超时，请检查网络或换用手机流量"
    }
    if (chain.any { it is UnknownHostException }) {
      return "服务器地址不正确：$host"
    }
    if (chain.any { it is SSLException }) {
      return "SSL/证书错误，请检查端口设置或换用手机流量"
    }
    if (chain.any { it is SocketException }) {
      return "无法连接 $host:$port，当前网络可能封锁邮件端口，请换手机流量再试"
    }
    if (chain.any { it is MessagingException }) {
      return "无法连接 $host:$port，请检查服务器/端口或换手机流量"
    }
    return e.message ?: "未知错误"
  }

  /** 连 QQ/163/北航等邮箱常因证书链不受信任导致连接被重置，这里信任所有证书。 */
  private fun trustAllSslContext(): SSLContext {
    val trustAll =
        arrayOf<TrustManager>(
            object : X509TrustManager {
              override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

              override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}

              override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )
    return SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
  }
}
