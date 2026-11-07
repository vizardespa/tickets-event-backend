package com.tickets.email

import com.tickets.email.ses.SesEmailSender
import software.amazon.awssdk.services.sesv2.SesV2Client

/**
 * Single place that chooses the email provider. Configure with:
 *  - EMAIL_PROVIDER: "logging" (default) or "ses"
 *  - EMAIL_FROM: sender address (required for ses; must be verified in SES)
 * For SES, region and credentials come from the standard AWS default provider chains.
 */
object EmailSenderFactory {
    fun fromEnvironment(): EmailSender = when (val provider = System.getenv("EMAIL_PROVIDER")?.lowercase() ?: "logging") {
        "logging" -> LoggingEmailSender()
        "ses" -> SesEmailSender(
            client = SesV2Client.create(),
            fromAddress = System.getenv("EMAIL_FROM")
                ?: error("EMAIL_FROM is required when EMAIL_PROVIDER=ses")
        )
        else -> error("Unknown EMAIL_PROVIDER '$provider'")
    }
}
