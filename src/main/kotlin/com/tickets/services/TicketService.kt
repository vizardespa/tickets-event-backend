package com.tickets.services

import com.tickets.db.TicketsTable
import com.tickets.email.TicketEmailService
import com.tickets.models.ManualTicketRequest
import com.tickets.models.Ticket
import com.tickets.models.TicketPaymentMethod
import com.tickets.models.TicketRequest
import com.tickets.models.TicketStatus
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * MySQL-backed business logic for tickets, stored in [TicketsTable].
 * Coordinates with [EventService] to keep seat availability consistent, and with
 * [ClipPaymentRepository] so every CLIP-paid ticket stays linked to its payment status.
 */
class TicketService(
    private val eventService: EventService,
    private val clipPaymentRepository: ClipPaymentRepository,
    private val ticketTypeService: TicketTypeService,
    private val ticketEmailService: TicketEmailService
) {

    private val paymentLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    private fun ResultRow.toTicket() = Ticket(
        id = this[TicketsTable.id],
        eventId = this[TicketsTable.eventId],
        ownerName = this[TicketsTable.ownerName],
        ownerEmail = this[TicketsTable.ownerEmail],
        seatNumber = this[TicketsTable.seatNumber],
        status = TicketStatus.valueOf(this[TicketsTable.status]),
        purchasedAt = this[TicketsTable.purchasedAt],
        paymentMethod = TicketPaymentMethod.valueOf(this[TicketsTable.paymentMethod]),
        clipPaymentId = this[TicketsTable.clipPaymentId],
        paymentNotes = this[TicketsTable.paymentNotes],
        ticketTypeId = this[TicketsTable.ticketTypeId],
        price = this[TicketsTable.price]
    )

    fun getAll(): List<Ticket> = transaction {
        TicketsTable.selectAll()
            .orderBy(TicketsTable.purchasedAt to SortOrder.DESC)
            .map { it.toTicket() }
    }

    fun getById(id: String): Ticket = transaction {
        TicketsTable.selectAll().where { TicketsTable.id eq id }
            .map { it.toTicket() }
            .firstOrNull()
    } ?: throw NotFoundException("Ticket with id '$id' not found")

    fun getByEvent(eventId: String): List<Ticket> = transaction {
        TicketsTable.selectAll().where { TicketsTable.eventId eq eventId }
            .map { it.toTicket() }
    }

    /**
     * Purchase a ticket for an event paid via CLIP. The referenced CLIP payment must already be
     * successful and not linked to another ticket, so the ticket can always be traced back to its
     * payment status.
     */
    fun purchase(request: TicketRequest): Ticket {
        if (request.clipPaymentId.isBlank()) {
            throw BadRequestException("clipPaymentId is required")
        }
        val lock = paymentLocks.computeIfAbsent(request.clipPaymentId) { Any() }
        return synchronized(lock) { purchaseLocked(request) }
    }

    /**
     * Called when CLIP reports a payment (e.g. via webhook). If the payment is approved, still has
     * no ticket and has saved pending details (3DS flow), creates the ticket. Returns null when
     * there is nothing to do.
     */
    fun completePendingTicket(paymentId: String): Ticket? {
        val payment = clipPaymentRepository.getById(paymentId) ?: return null
        val pending = payment.pendingTicket ?: return null
        if (payment.ticketId != null || !payment.status.equals("approved", ignoreCase = true)) {
            return null
        }
        return purchase(
            TicketRequest(
                eventId = pending.eventId,
                ownerName = pending.ownerName,
                ownerEmail = pending.ownerEmail,
                clipPaymentId = paymentId,
                ticketTypeId = pending.ticketTypeId
            )
        )
    }

    private fun purchaseLocked(request: TicketRequest): Ticket {
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
            val existing = getById(payment.ticketId)
            if (existing.eventId == request.eventId && existing.ticketTypeId == request.ticketTypeId) {
                return existing
            }
            throw BadRequestException(
                "CLIP payment '${request.clipPaymentId}' is already linked to a ticket"
            )
        }
        if (!payment.status.equals("approved", ignoreCase = true)) {
            throw BadRequestException(
                "CLIP payment '${request.clipPaymentId}' is not successful (status=${payment.status})"
            )
        }

        val event = eventService.getById(request.eventId)

        val ticketType = ticketTypeService.getById(request.ticketTypeId)
        if (ticketType.eventId != request.eventId) {
            throw BadRequestException(
                "Ticket type '${request.ticketTypeId}' does not belong to event '${request.eventId}'"
            )
        }
        // Purchase-window restriction only applies to CLIP-paid purchases.
        ticketTypeService.validatePurchaseWindow(ticketType)

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
            clipPaymentId = request.clipPaymentId,
            ticketTypeId = ticketType.id,
            price = ticketType.price
        )
        insertTicket(ticket)
        clipPaymentRepository.linkTicket(request.clipPaymentId, ticket.id)
        ticketEmailService.sendPurchaseConfirmation(ticket, event)
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

        // Manual entries are never subject to the ticket type's purchase window: only confirm
        // the ticket type belongs to this event, skip the date check entirely.
        val ticketType = ticketTypeService.getById(request.ticketTypeId)
        if (ticketType.eventId != request.eventId) {
            throw BadRequestException(
                "Ticket type '${request.ticketTypeId}' does not belong to event '${request.eventId}'"
            )
        }

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
            paymentNotes = request.paymentNotes,
            ticketTypeId = ticketType.id,
            price = ticketType.price
        )
        insertTicket(ticket)
        ticketEmailService.sendPurchaseConfirmation(ticket, event)
        return ticket
    }

    private fun insertTicket(ticket: Ticket) {
        transaction {
            TicketsTable.insert {
                it[id] = ticket.id
                it[eventId] = ticket.eventId
                it[ownerName] = ticket.ownerName
                it[ownerEmail] = ticket.ownerEmail
                it[seatNumber] = ticket.seatNumber
                it[status] = ticket.status.name
                it[purchasedAt] = ticket.purchasedAt
                it[paymentMethod] = ticket.paymentMethod.name
                it[clipPaymentId] = ticket.clipPaymentId
                it[paymentNotes] = ticket.paymentNotes
                it[ticketTypeId] = ticket.ticketTypeId
                it[price] = ticket.price
            }
        }
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
        transaction {
            TicketsTable.update({ TicketsTable.id eq id }) {
                it[status] = cancelled.status.name
            }
        }
        eventService.releaseSeat(ticket.eventId)
        return cancelled
    }
}
