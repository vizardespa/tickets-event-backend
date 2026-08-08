package com.tickets.services

import com.tickets.models.Ticket
import com.tickets.models.TicketRequest
import com.tickets.models.TicketStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory business logic for tickets, backed by a concurrent map.
 * Coordinates with [EventService] to keep seat availability consistent.
 */
class TicketService(private val eventService: EventService) {

    private val tickets = ConcurrentHashMap<String, Ticket>()

    fun getAll(): List<Ticket> = tickets.values.sortedByDescending { it.purchasedAt }

    fun getById(id: String): Ticket =
        tickets[id] ?: throw NotFoundException("Ticket with id '$id' not found")

    fun getByEvent(eventId: String): List<Ticket> =
        tickets.values.filter { it.eventId == eventId }

    /**
     * Purchase a ticket for an event. Reserves a seat atomically and assigns
     * a seat number if one was not explicitly requested.
     */
    fun purchase(request: TicketRequest): Ticket {
        if (request.ownerName.isBlank()) {
            throw BadRequestException("ownerName is required")
        }
        if (request.ownerEmail.isBlank()) {
            throw BadRequestException("ownerEmail is required")
        }

        val event = eventService.getById(request.eventId)

        // Determine seat number: use requested one if free, else next available.
        val activeSeats = getByEvent(request.eventId)
            .filter { it.status == TicketStatus.ACTIVE }
            .map { it.seatNumber }
            .toSet()

        val seatNumber = request.seatNumber ?: run {
            (1..event.totalSeats).firstOrNull { it !in activeSeats }
                ?: throw BadRequestException("No seats available for event '${event.name}'")
        }

        if (seatNumber < 1 || seatNumber > event.totalSeats) {
            throw BadRequestException("seatNumber must be between 1 and ${event.totalSeats}")
        }
        if (seatNumber in activeSeats) {
            throw BadRequestException("Seat $seatNumber is already taken")
        }

        // Reserve a seat on the event (atomic, throws if sold out).
        eventService.reserveSeat(request.eventId)

        val ticket = Ticket(
            id = UUID.randomUUID().toString(),
            eventId = request.eventId,
            ownerName = request.ownerName,
            ownerEmail = request.ownerEmail,
            seatNumber = seatNumber,
            status = TicketStatus.ACTIVE,
            purchasedAt = Instant.now().toString()
        )
        tickets[ticket.id] = ticket
        return ticket
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
