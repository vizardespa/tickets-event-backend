package com.tickets.services

import com.tickets.db.EventsTable
import com.tickets.models.Event
import com.tickets.models.EventRequest
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * MySQL-backed business logic for events, stored in [EventsTable].
 */
class EventService {

    private fun ResultRow.toEvent() = Event(
        id = this[EventsTable.id],
        name = this[EventsTable.name],
        description = this[EventsTable.description],
        venue = this[EventsTable.venue],
        date = this[EventsTable.date],
        totalSeats = this[EventsTable.totalSeats],
        availableSeats = this[EventsTable.availableSeats],
        price = this[EventsTable.price],
        createdAt = this[EventsTable.createdAt]
    )

    fun getAll(): List<Event> = transaction {
        EventsTable.selectAll()
            .orderBy(EventsTable.date to SortOrder.ASC)
            .map { it.toEvent() }
    }

    fun getById(id: String): Event = transaction {
        EventsTable.selectAll().where { EventsTable.id eq id }
            .map { it.toEvent() }
            .firstOrNull()
    } ?: throw NotFoundException("Event with id '$id' not found")

    fun create(request: EventRequest): Event {
        if (request.totalSeats < 0) {
            throw BadRequestException("totalSeats must be >= 0")
        }
        val available = request.availableSeats ?: request.totalSeats
        if (available < 0 || available > request.totalSeats) {
            throw BadRequestException("availableSeats must be between 0 and totalSeats")
        }
        val event = Event(
            id = UUID.randomUUID().toString(),
            name = request.name,
            description = request.description,
            venue = request.venue,
            date = request.date,
            totalSeats = request.totalSeats,
            availableSeats = available,
            price = request.price,
            createdAt = Instant.now().toString()
        )
        transaction {
            EventsTable.insert {
                it[id] = event.id
                it[name] = event.name
                it[description] = event.description
                it[venue] = event.venue
                it[date] = event.date
                it[totalSeats] = event.totalSeats
                it[availableSeats] = event.availableSeats
                it[price] = event.price
                it[createdAt] = event.createdAt
            }
        }
        return event
    }

    fun update(id: String, request: EventRequest): Event {
        val existing = getById(id)
        if (request.totalSeats < 0) {
            throw BadRequestException("totalSeats must be >= 0")
        }
        val available = request.availableSeats ?: existing.availableSeats
        if (available < 0 || available > request.totalSeats) {
            throw BadRequestException("availableSeats must be between 0 and totalSeats")
        }
        val updated = existing.copy(
            name = request.name,
            description = request.description,
            venue = request.venue,
            date = request.date,
            totalSeats = request.totalSeats,
            availableSeats = available,
            price = request.price
        )
        transaction {
            EventsTable.update({ EventsTable.id eq id }) {
                it[name] = updated.name
                it[description] = updated.description
                it[venue] = updated.venue
                it[date] = updated.date
                it[totalSeats] = updated.totalSeats
                it[availableSeats] = updated.availableSeats
                it[price] = updated.price
            }
        }
        return updated
    }

    fun delete(id: String) {
        val deleted = transaction {
            EventsTable.deleteWhere { EventsTable.id eq id }
        }
        if (deleted == 0) {
            throw NotFoundException("Event with id '$id' not found")
        }
    }

    /**
     * Atomically decrement available seats when a ticket is purchased.
     * Returns the updated event. Throws if the event is missing or sold out.
     */
    fun reserveSeat(eventId: String): Event = transaction {
        val updatedRows = EventsTable.update({
            (EventsTable.id eq eventId) and (EventsTable.availableSeats greater 0)
        }) {
            it[availableSeats] = EventsTable.availableSeats - 1
        }
        if (updatedRows == 0) {
            val existing = EventsTable.selectAll().where { EventsTable.id eq eventId }
                .map { it.toEvent() }
                .firstOrNull() ?: throw NotFoundException("Event with id '$eventId' not found")
            throw BadRequestException("Event '${existing.name}' is sold out")
        }
        EventsTable.selectAll().where { EventsTable.id eq eventId }.map { it.toEvent() }.first()
    }

    /**
     * Atomically increment available seats when a ticket is cancelled.
     */
    fun releaseSeat(eventId: String) {
        transaction {
            EventsTable.update({
                (EventsTable.id eq eventId) and (EventsTable.availableSeats less EventsTable.totalSeats)
            }) {
                it[availableSeats] = EventsTable.availableSeats + 1
            }
        }
    }

    fun exists(id: String): Boolean = transaction {
        EventsTable.selectAll().where { EventsTable.id eq id }.limit(1).any()
    }
}
