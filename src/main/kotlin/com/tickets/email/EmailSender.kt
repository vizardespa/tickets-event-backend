package com.tickets.email

data class EmailMessage(
    val to: String,
    val subject: String,
    val textBody: String,
    val htmlBody: String? = null
)

/**
 * Infrastructure port: delivers an already-built email. Implementations (SES, SMTP, logging...)
 * live behind this interface; nothing outside of [EmailSenderFactory] should know about them.
 */
interface EmailSender {
    fun send(message: EmailMessage)
}

class EmailSendException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
