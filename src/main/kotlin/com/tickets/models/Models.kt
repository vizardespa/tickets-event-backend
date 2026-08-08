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
    val purchasedAt: String
)

/**
 * Payload used when purchasing/creating a ticket.
 * seatNumber is optional — if omitted the service assigns the next available seat.
 */
@Serializable
data class TicketRequest(
    val eventId: String,
    val ownerName: String,
    val ownerEmail: String,
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
