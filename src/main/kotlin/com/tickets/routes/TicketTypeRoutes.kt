package com.tickets.routes

import com.tickets.models.TicketTypeRequest
import com.tickets.services.BadRequestException
import com.tickets.services.TicketTypeService
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

fun Route.ticketTypeRoutes(ticketTypeService: TicketTypeService) {
    route("/api/events/{eventId}/ticket-types") {

        // List ticket types for an event
        get {
            val eventId = call.parameters["eventId"]
                ?: throw BadRequestException("Missing event id")
            call.respond(ticketTypeService.getByEvent(eventId))
        }

        // Restricted: create a ticket type for an event
        post {
            requireAdminKey(call)
            val eventId = call.parameters["eventId"]
                ?: throw BadRequestException("Missing event id")
            val request = call.receive<TicketTypeRequest>()
            val created = ticketTypeService.create(eventId, request)
            call.respond(HttpStatusCode.Created, created)
        }
    }

    route("/api/ticket-types/{id}") {

        // Get ticket type by id
        get {
            val id = call.parameters["id"]
                ?: throw BadRequestException("Missing ticket type id")
            call.respond(ticketTypeService.getById(id))
        }

        // Restricted: update ticket type
        put {
            requireAdminKey(call)
            val id = call.parameters["id"]
                ?: throw BadRequestException("Missing ticket type id")
            val request = call.receive<TicketTypeRequest>()
            call.respond(ticketTypeService.update(id, request))
        }

        // Restricted: delete ticket type
        delete {
            requireAdminKey(call)
            val id = call.parameters["id"]
                ?: throw BadRequestException("Missing ticket type id")
            ticketTypeService.delete(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
