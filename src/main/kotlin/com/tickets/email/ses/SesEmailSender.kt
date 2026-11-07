package com.tickets.email.ses

import com.tickets.email.EmailMessage
import com.tickets.email.EmailSendException
import com.tickets.email.EmailSender
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.Body
import software.amazon.awssdk.services.sesv2.model.Content
import software.amazon.awssdk.services.sesv2.model.Destination
import software.amazon.awssdk.services.sesv2.model.EmailContent
import software.amazon.awssdk.services.sesv2.model.Message
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest

/** AWS SES implementation of [EmailSender]. The only class that touches the AWS SDK. */
class SesEmailSender(
    private val client: SesV2Client,
    private val fromAddress: String
) : EmailSender {

    override fun send(message: EmailMessage) {
        val body = Body.builder()
            .text(content(message.textBody))
            .apply { message.htmlBody?.let { html(content(it)) } }
            .build()
        val request = SendEmailRequest.builder()
            .fromEmailAddress(fromAddress)
            .destination(Destination.builder().toAddresses(message.to).build())
            .content(
                EmailContent.builder()
                    .simple(Message.builder().subject(content(message.subject)).body(body).build())
                    .build()
            )
            .build()
        try {
            client.sendEmail(request)
        } catch (e: Exception) {
            throw EmailSendException("SES failed to send email to ${message.to}", e)
        }
    }

    private fun content(data: String) = Content.builder().data(data).charset("UTF-8").build()
}
