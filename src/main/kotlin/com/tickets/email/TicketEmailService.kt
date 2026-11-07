package com.tickets.email

import com.tickets.models.Event
import com.tickets.models.Ticket
import org.slf4j.LoggerFactory

/** Domain-level notifications about tickets. Knows nothing about how email is delivered. */
interface TicketEmailService {
    fun sendPurchaseConfirmation(ticket: Ticket, event: Event)
}

class DefaultTicketEmailService(private val emailSender: EmailSender) : TicketEmailService {
    private val log = LoggerFactory.getLogger(DefaultTicketEmailService::class.java)

    /** Never throws: a failed email must not undo a completed purchase. */
    override fun sendPurchaseConfirmation(ticket: Ticket, event: Event) {
        try {
            emailSender.send(
                EmailMessage(
                    to = ticket.ownerEmail,
                    subject = "Your ticket for ${event.name}",
                    textBody = buildString {
                        appendLine("Hi ${ticket.ownerName},")
                        appendLine()
                        appendLine("Your purchase was successful. Here are your ticket details:")
                        appendLine()
                        appendLine("Event: ${event.name}")
                        appendLine("Venue: ${event.venue}")
                        appendLine("Date: ${event.date}")
                        appendLine("Seat: ${ticket.seatNumber}")
                        appendLine("Ticket ID: ${ticket.id}")
                        appendLine()
                        append("See you there!")
                    }
                )
            )
        } catch (e: Exception) {
            log.error("Failed to send purchase confirmation for ticket {}", ticket.id, e)
        }
    }
}
