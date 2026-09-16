package cn.edu.buaa.hzcampus.model.dto

import kotlinx.serialization.Serializable

/** 邮箱账号配置。 */
@Serializable
data class MailAccount(
    val id: String,
    val name: String,
    val email: String,
    val password: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
    val ssl: Boolean = true,
)

/** 邮件消息摘要。 */
@Serializable
data class MailMessage(
    val uid: String,
    val subject: String,
    val from: String,
    val date: String,
    val unread: Boolean,
    val bodyPreview: String,
)
