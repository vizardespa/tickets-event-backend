package com.tickets.services

import com.tickets.models.ManualTicketRequest
import com.tickets.models.Ticket
import com.tickets.models.TicketPaymentMethod
import com.tickets.models.TicketRequest
import com.tickets.models.TicketStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory business logic for tickets, backed by a concurrent map.
 * Coordinates with [EventService] to keep seat availability consistent, and with
 * [ClipPaymentRepository] so every CLIP-paid ticket stays linked to its payment status.
 */
class TicketService(
    private val eventService: EventService,
    private val clipPaymentRepository: ClipPaymentRepository
) {

    private val tickets = ConcurrentHashMap<String, Ticket>()

    fun getAll(): List<Ticket> = tickets.values.sortedByDescending { it.purchasedAt }

    fun getById(id: String): Ticket =
        tickets[id] ?: throw NotFoundException("Ticket with id '$id' not found")

    fun getByEvent(eventId: String): List<Ticket> =
        tickets.values.filter { it.eventId == eventId }

    /**
     * Purchase a ticket for an event paid via CLIP. The referenced CLIP payment must already be
     * successful and not linked to another ticket, so the ticket can always be traced back to its
     * payment status.
     */
    fun purchase(request: TicketRequest): Ticket {
        if (request.ownerName.isBlank()) {
            throw BadRequestException("ownerName is required")
        }
        if (request.ownerEmail.isBlank()) {
            throw BadRequestException("ownerEmail is required")
        }
        if (request.clipPaymentId.isBlank()) {
            throw BadRequestException("clipPaymentId is required")
        }

        val payment = clipPaymentRepository.requireById(request.clipPaymentId)
        if (payment.ticketId != null) {
            throw BadRequestException(
                "CLIP payment '${request.clipPaymentId}' is already linked to a ticket"
            )
        }
        if (!payment.status.equals("success", ignoreCase = true)) {
            throw BadRequestException(
                "CLIP payment '${request.clipPaymentId}' is not successful (status=${payment.status})"
            )
        }

        val event = eventService.getById(request.eventId)
        val seatNumber = resolveSeatNumber(request.eventId, request.seatNumber, event.totalSeats, event.name)

        // Reserve a seat on the event (atomic, throws if sold out).
        eventService.reserveSeat(request.eventId)

        val ticket = Ticket(
            id = UUID.randomUUID().toString(),
            eventId = request.eventId,
            ownerName = request.ownerName,
            ownerEmail = request.ownerEmail,
            seatNumber = seatNumber,
            status = TicketStatus.ACTIVE,
            purchasedAt = Instant.now().toString(),
            paymentMethod = TicketPaymentMethod.CLIP,
            clipPaymentId = request.clipPaymentId
        )
        tickets[ticket.id] = ticket
        clipPaymentRepository.linkTicket(request.clipPaymentId, ticket.id)
        return ticket
    }

    /**
     * Restricted path for tickets paid by an alternative method (e.g. cash). No CLIP payment is
     * involved; callers must be authorized before invoking this (see [requireAdminKey]).
     */
    fun purchaseManual(request: ManualTicketRequest): Ticket {
        if (request.ownerName.isBlank()) {
            throw BadRequestException("ownerName is required")
        }
        if (request.ownerEmail.isBlank()) {
            throw BadRequestException("ownerEmail is required")
        }
        if (request.paymentNotes.isBlank()) {
            throw BadRequestException("paymentNotes is required")
        }

        val event = eventService.getById(request.eventId)
        val seatNumber = resolveSeatNumber(request.eventId, request.seatNumber, event.totalSeats, event.name)

        eventService.reserveSeat(request.eventId)

        val ticket = Ticket(
            id = UUID.randomUUID().toString(),
            eventId = request.eventId,
            ownerName = request.ownerName,
            ownerEmail = request.ownerEmail,
            seatNumber = seatNumber,
            status = TicketStatus.ACTIVE,
            purchasedAt = Instant.now().toString(),
            paymentMethod = TicketPaymentMethod.MANUAL,
            paymentNotes = request.paymentNotes
        )
        tickets[ticket.id] = ticket
        return ticket
    }

    /** Resolves/validates the seat number to assign, using the next free seat if none was requested. */
    private fun resolveSeatNumber(eventId: String, requestedSeat: Int?, totalSeats: Int, eventName: String): Int {
        val activeSeats = getByEvent(eventId)
            .filter { it.status == TicketStatus.ACTIVE }
            .map { it.seatNumber }
            .toSet()

        val seatNumber = requestedSeat ?: run {
            (1..totalSeats).firstOrNull { it !in activeSeats }
                ?: throw BadRequestException("No seats available for event '$eventName'")
        }

        if (seatNumber < 1 || seatNumber > totalSeats) {
            throw BadRequestException("seatNumber must be between 1 and $totalSeats")
        }
        if (seatNumber in activeSeats) {
            throw BadRequestException("Seat $seatNumber is already taken")
        }
        return seatNumber
    }

    /**
     * Cancel a ticket. Marks it CANCELLED and releases the seat back to the event.
     */
    fun cancel(id: String): Ticket {
        val ticket = getById(id)
        if (ticket.status == TicketStatus.CANCELLED) {
            throw BadRequestException("Ticket '$id' is already cancelled")
        }
        val cancelled = ticket.copy(status = TicketStatus.CANCELLED)
        tickets[id] = cancelled
        eventService.releaseSeat(ticket.eventId)
        return cancelled
    }
}
