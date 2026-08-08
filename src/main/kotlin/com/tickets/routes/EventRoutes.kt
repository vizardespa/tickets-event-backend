package com.tickets.routes

import com.tickets.models.EventRequest
import com.tickets.services.BadRequestException
import com.tickets.services.EventService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.eventRoutes(eventService: EventService) {
    route("/api/events") {

        // List all events
        get {
            call.respond(eventService.getAll())
        }

        // Get event by id
        get("/{id}") {
            val id = call.parameters["id"]
                ?: throw BadRequestException("Missing event id")
            call.respond(eventService.getById(id))
        }

        // Create event
        post {
            val request = call.receive<EventRequest>()
            val created = eventService.create(request)
            call.respond(HttpStatusCode.Created, created)
        }

        // Update event
        put("/{id}") {
            val id = call.parameters["id"]
                ?: throw BadRequestException("Missing event id")
            val request = call.receive<EventRequest>()
            call.respond(eventService.update(id, request))
        }

        // Delete event
        delete("/{id}") {
            val id = call.parameters["id"]
                ?: throw BadRequestException("Missing event id")
            eventService.delete(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
