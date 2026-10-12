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
    val price: Double,
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
    val availableSeats: Int? = null,
    val price: Double
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
    val paymentNotes: String? = null
)

/**
 * Payload used when purchasing/creating a ticket paid via CLIP.
 * seatNumber is optional — if omitted the service assigns the next available seat.
 * clipPaymentId must reference an already-successful CLIP payment (see /api/payments/clip),
 * so every ticket created this way can be traced back to its payment status.
 */
@Serializable
data class TicketRequest(
    val eventId: String,
    val ownerName: String,
    val ownerEmail: String,
    val clipPaymentId: String,
    val seatNumber: Int? = null
)

/**
 * Restricted payload used to record a ticket paid by an alternative method (e.g. cash).
 * No CLIP payment is involved; paymentNotes should capture how/when it was collected.
 */
@Serializable
data class ManualTicketRequest(
    val eventId: String,
    val ownerName: String,
    val ownerEmail: String,
    val paymentNotes: String,
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
