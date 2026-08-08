package com.tickets.services

import com.tickets.models.Event
import com.tickets.models.EventRequest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory business logic for events, backed by a concurrent map.
 */
class EventService {

    private val events = ConcurrentHashMap<String, Event>()

    init {
        // Seed a couple of sample events so the API is useful out of the box.
        val sample1 = Event(
            id = UUID.randomUUID().toString(),
            name = "Kotlin Conf 2026",
            description = "Annual conference for Kotlin developers.",
            venue = "Amsterdam Convention Center",
            date = "2026-05-14T09:00:00Z",
            totalSeats = 500,
            availableSeats = 500,
            price = 299.0,
            createdAt = Instant.now().toString()
        )
        val sample2 = Event(
            id = UUID.randomUUID().toString(),
            name = "Live Jazz Night",
            description = "An evening of smooth jazz.",
            venue = "Blue Note Club",
            date = "2026-03-02T20:00:00Z",
            totalSeats = 80,
            availableSeats = 80,
            price = 45.5,
            createdAt = Instant.now().toString()
        )
        events[sample1.id] = sample1
        events[sample2.id] = sample2
    }

    fun getAll(): List<Event> = events.values.sortedBy { it.date }

    fun getById(id: String): Event =
        events[id] ?: throw NotFoundException("Event with id '$id' not found")

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
        events[event.id] = event
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
        events[id] = updated
        return updated
    }

    fun delete(id: String) {
        events.remove(id) ?: throw NotFoundException("Event with id '$id' not found")
    }

    /**
     * Atomically decrement available seats when a ticket is purchased.
     * Returns the updated event. Throws if the event is missing or sold out.
     */
    fun reserveSeat(eventId: String): Event {
        return events.compute(eventId) { _, current ->
            if (current == null) {
                throw NotFoundException("Event with id '$eventId' not found")
            }
            if (current.availableSeats <= 0) {
                throw BadRequestException("Event '${current.name}' is sold out")
            }
            current.copy(availableSeats = current.availableSeats - 1)
        }!!
    }

    /**
     * Atomically increment available seats when a ticket is cancelled.
     */
    fun releaseSeat(eventId: String) {
        events.compute(eventId) { _, current ->
            if (current == null) {
                return@compute null
            }
            if (current.availableSeats < current.totalSeats) {
                current.copy(availableSeats = current.availableSeats + 1)
            } else {
                current
            }
        }
    }

    fun exists(id: String): Boolean = events.containsKey(id)
}
