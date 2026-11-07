package com.tickets.email

import org.slf4j.LoggerFactory

/** Dev/local implementation: logs the email instead of sending it. */
class LoggingEmailSender : EmailSender {
    private val log = LoggerFactory.getLogger(LoggingEmailSender::class.java)

    override fun send(message: EmailMessage) {
        log.info("Email (not sent, logging provider) to={} subject={}\n{}", message.to, message.subject, message.textBody)
    }
}
