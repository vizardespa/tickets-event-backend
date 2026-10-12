package com.tickets.services

import com.tickets.db.TicketTypesTable
import com.tickets.models.TicketType
import com.tickets.models.TicketTypeRequest
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * MySQL-backed business logic for ticket types, stored in [TicketTypesTable].
 * A ticket type optionally defines a purchase window: a null [TicketType.purchaseStartAt] means
 * purchases are open immediately, a null [TicketType.purchaseEndAt] means they never close.
 */
class TicketTypeService(private val eventService: EventService) {

    private fun ResultRow.toTicketType() = TicketType(
        id = this[TicketTypesTable.id],
        eventId = this[TicketTypesTable.eventId],
        name = this[TicketTypesTable.name],
        description = this[TicketTypesTable.description],
        price = this[TicketTypesTable.price],
        purchaseStartAt = this[TicketTypesTable.purchaseStartAt],
        purchaseEndAt = this[TicketTypesTable.purchaseEndAt],
        createdAt = this[TicketTypesTable.createdAt]
    )

    fun getByEvent(eventId: String): List<TicketType> = transaction {
        TicketTypesTable.selectAll().where { TicketTypesTable.eventId eq eventId }
            .orderBy(TicketTypesTable.createdAt to SortOrder.ASC)
            .map { it.toTicketType() }
    }

    fun getById(id: String): TicketType = transaction {
        TicketTypesTable.selectAll().where { TicketTypesTable.id eq id }
            .map { it.toTicketType() }
            .firstOrNull()
    } ?: throw NotFoundException("Ticket type with id '$id' not found")

    fun create(eventId: String, request: TicketTypeRequest): TicketType {
        eventService.getById(eventId)
        validateRequest(request)

        val ticketType = TicketType(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            name = request.name,
            description = request.description,
            price = request.price,
            purchaseStartAt = request.purchaseStartAt,
            purchaseEndAt = request.purchaseEndAt,
            createdAt = Instant.now().toString()
        )
        transaction {
            TicketTypesTable.insert {
                it[id] = ticketType.id
                it[this.eventId] = ticketType.eventId
                it[name] = ticketType.name
                it[description] = ticketType.description
                it[price] = ticketType.price
                it[purchaseStartAt] = ticketType.purchaseStartAt
                it[purchaseEndAt] = ticketType.purchaseEndAt
                it[createdAt] = ticketType.createdAt
            }
        }
        return ticketType
    }

    fun update(id: String, request: TicketTypeRequest): TicketType {
        val existing = getById(id)
        validateRequest(request)

        val updated = existing.copy(
            name = request.name,
            description = request.description,
            price = request.price,
            purchaseStartAt = request.purchaseStartAt,
            purchaseEndAt = request.purchaseEndAt
        )
        transaction {
            TicketTypesTable.update({ TicketTypesTable.id eq id }) {
                it[name] = updated.name
                it[description] = updated.description
                it[price] = updated.price
                it[purchaseStartAt] = updated.purchaseStartAt
                it[purchaseEndAt] = updated.purchaseEndAt
            }
        }
        return updated
    }

    fun delete(id: String) {
        val deleted = transaction {
            TicketTypesTable.deleteWhere { TicketTypesTable.id eq id }
        }
        if (deleted == 0) {
            throw NotFoundException("Ticket type with id '$id' not found")
        }
    }

    private fun validateRequest(request: TicketTypeRequest) {
        if (request.name.isBlank()) {
            throw BadRequestException("name is required")
        }
        if (request.price < 0) {
            throw BadRequestException("price must be >= 0")
        }
        val start = parseInstantOrNull(request.purchaseStartAt, "purchaseStartAt")
        val end = parseInstantOrNull(request.purchaseEndAt, "purchaseEndAt")
        if (start != null && end != null && start.isAfter(end)) {
            throw BadRequestException("purchaseStartAt must not be after purchaseEndAt")
        }
    }

    private fun parseInstantOrNull(value: String?, field: String): Instant? {
        if (value == null) return null
        return try {
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            throw BadRequestException("$field must be a valid ISO-8601 instant")
        }
    }

    /**
     * Enforces the purchase window for a ticket type. Only invoked by the CLIP purchase flow —
     * manual ticket creation never calls this, so it is never subject to the date restriction.
     */
    fun validatePurchaseWindow(ticketType: TicketType) {
        val now = Instant.now()
        ticketType.purchaseStartAt?.let {
            if (now.isBefore(Instant.parse(it))) {
                throw TicketPurchaseWindowException(
                    "Ticket type '${ticketType.name}' is not yet on sale (starts $it)"
                )
            }
        }
        ticketType.purchaseEndAt?.let {
            if (now.isAfter(Instant.parse(it))) {
                throw TicketPurchaseWindowException(
                    "Ticket type '${ticketType.name}' is no longer on sale (ended $it)"
                )
            }
        }
    }
}
