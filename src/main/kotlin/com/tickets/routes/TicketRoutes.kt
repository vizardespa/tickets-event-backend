package com.tickets.routes

import com.tickets.models.TicketRequest
import com.tickets.services.BadRequestException
import com.tickets.services.TicketService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.ticketRoutes(ticketService: TicketService) {
    route("/api/tickets") {

        // List all tickets
        get {
            call.respond(ticketService.getAll())
        }

        // Get ticket by id
        get("/{id}") {
            val id = call.parameters["id"]
                ?: throw BadRequestException("Missing ticket id")
            call.respond(ticketService.getById(id))
        }

        // Purchase/create a ticket
        post {
            val request = call.receive<TicketRequest>()
            val ticket = ticketService.purchase(request)
            call.respond(HttpStatusCode.Created, ticket)
        }

        // Cancel a ticket
        delete("/{id}") {
            val id = call.parameters["id"]
                ?: throw BadRequestException("Missing ticket id")
            val cancelled = ticketService.cancel(id)
            call.respond(HttpStatusCode.OK, cancelled)
        }
    }
}
