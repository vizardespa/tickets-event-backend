package com.tickets.db

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definitions mirroring the domain models in `com.tickets.models`.
 */
object EventsTable : Table("events") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)
    val description = text("description")
    val venue = varchar("venue", 255)
    val date = varchar("date", 64)
    val totalSeats = integer("total_seats")
    val availableSeats = integer("available_seats")
    val price = double("price")
    val createdAt = varchar("created_at", 64)

    override val primaryKey = PrimaryKey(id)
}

object ClipPaymentsTable : Table("clip_payments") {
    val id = varchar("id", 64)
    val ticketId = varchar("ticket_id", 36).nullable()
    val status = varchar("status", 64).nullable()
    val statusDetailCode = varchar("status_detail_code", 64).nullable()
    val statusDetailMessage = text("status_detail_message").nullable()
    val amount = double("amount").nullable()
    val currency = varchar("currency", 16).nullable()
    val description = text("description").nullable()
    val receiptNo = varchar("receipt_no", 64).nullable()
    val clipCreatedAt = varchar("clip_created_at", 64).nullable()
    val recordedAt = varchar("recorded_at", 64)
    val updatedAt = varchar("updated_at", 64)

    override val primaryKey = PrimaryKey(id)
}

object TicketsTable : Table("tickets") {
    val id = varchar("id", 36)
    val eventId = varchar("event_id", 36).references(EventsTable.id)
    val ownerName = varchar("owner_name", 255)
    val ownerEmail = varchar("owner_email", 255)
    val seatNumber = integer("seat_number")
    val status = varchar("status", 32)
    val purchasedAt = varchar("purchased_at", 64)
    val paymentMethod = varchar("payment_method", 32)
    val clipPaymentId = varchar("clip_payment_id", 64).references(ClipPaymentsTable.id).nullable()
    val paymentNotes = text("payment_notes").nullable()

    override val primaryKey = PrimaryKey(id)
}
