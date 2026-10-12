package com.tickets.models

import kotlinx.serialization.Serializable

/**
 * Represents an event that tickets can be purchased for.
 */
@Serializable
data class Event(
    val id: String,
    val name: String,
    val description: String,
    val venue: String,
    val date: String,
    val totalSeats: Int,
    val availableSeats: Int,
    val createdAt: String
)

/**
 * Payload used when creating or updating an event.
 * availableSeats is optional on create (defaults to totalSeats).
 */
@Serializable
data class EventRequest(
    val name: String,
    val description: String = "",
    val venue: String,
    val date: String,
    val totalSeats: Int,
    val availableSeats: Int? = null
)

/**
 * A category of ticket for an event (e.g. "general", "pre-sale"), each with its own [price].
 * Optionally restricted to a purchase window: a null [purchaseStartAt] means purchases are open
 * immediately; a null [purchaseEndAt] means purchases never close.
 */
@Serializable
data class TicketType(
    val id: String,
    val eventId: String,
    val name: String,
    val description: String,
    val price: Double,
    val purchaseStartAt: String? = null,
    val purchaseEndAt: String? = null,
    val createdAt: String
)

/**
 * Payload used when creating or updating a ticket type for an event.
 */
@Serializable
data class TicketTypeRequest(
    val name: String,
    val description: String = "",
    val price: Double,
    val purchaseStartAt: String? = null,
    val purchaseEndAt: String? = null
)

/**
 * Ticket status.
 */
@Serializable
enum class TicketStatus {
    ACTIVE,
    CANCELLED
}

/**
 * How a ticket was paid for.
 */
@Serializable
enum class TicketPaymentMethod {
    /** Paid online via CLIP's Checkout Transparente. Always has a [Ticket.clipPaymentId]. */
    CLIP,
    /** Paid via an alternative method (e.g. cash) recorded manually by staff. */
    MANUAL
}

/**
 * Represents a ticket purchased for an event.
 */
@Serializable
data class Ticket(
    val id: String,
    val eventId: String,
    val ownerName: String,
    val ownerEmail: String,
    val seatNumber: Int,
    val status: TicketStatus,
    val purchasedAt: String,
    val paymentMethod: TicketPaymentMethod,
    val clipPaymentId: String? = null,
    val paymentNotes: String? = null,
    val ticketTypeId: String? = null,
    val price: Double? = null
)

/**
 * Payload used when purchasing/creating a ticket paid via CLIP.
 * seatNumber is optional — if omitted the service assigns the next available seat.
 * clipPaymentId must reference an already-successful CLIP payment (see /api/payments/clip),
 * so every ticket created this way can be traced back to its payment status. ticketTypeId is
 * required — its price is snapshotted onto the ticket and its purchase window is enforced.
 */
@Serializable
data class TicketRequest(
    val eventId: String,
    val ownerName: String,
    val ownerEmail: String,
    val clipPaymentId: String,
    val ticketTypeId: String,
    val seatNumber: Int? = null
)

/**
 * Restricted payload used to record a ticket paid by an alternative method (e.g. cash).
 * No CLIP payment is involved; paymentNotes should capture how/when it was collected.
 * ticketTypeId is required (its price is snapshotted onto the ticket), but unlike [TicketRequest]
 * its purchase window is never enforced for this manual path.
 */
@Serializable
data class ManualTicketRequest(
    val eventId: String,
    val ownerName: String,
    val ownerEmail: String,
    val paymentNotes: String,
    val ticketTypeId: String,
    val seatNumber: Int? = null
)

/**
 * Health check response body.
 */
@Serializable
data class HealthStatus(
    val status: String
)

/**
 * Standard error response body.
 */
@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)
