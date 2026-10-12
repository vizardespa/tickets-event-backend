package com.tickets.routes

import com.tickets.models.ManualTicketRequest
import com.tickets.models.TicketRequest
import com.tickets.services.BadRequestException
import com.tickets.services.ClipPaymentRepository
import com.tickets.services.NotFoundException
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

fun Route.ticketRoutes(ticketService: TicketService, clipPaymentRepository: ClipPaymentRepository) {
    route("/api/tickets") {

        // Restricted: list all tickets
        get {
            requireAdminKey(call)
            call.respond(ticketService.getAll())
        }

        // Restricted: get ticket by id
        get("/{id}") {
            requireAdminKey(call)
            val id = call.parameters["id"]
                ?: throw BadRequestException("Missing ticket id")
            call.respond(ticketService.getById(id))
        }

        // Purchase/create a ticket paid via CLIP (requires a successful clipPaymentId).
        post {
            val request = call.receive<TicketRequest>()
            val ticket = ticketService.purchase(request)
            call.respond(HttpStatusCode.Created, ticket)
        }

        // Restricted: record a ticket paid by an alternative method (e.g. cash).
        post("/manual") {
            requireAdminKey(call)
            val request = call.receive<ManualTicketRequest>()
            val ticket = ticketService.purchaseManual(request)
            call.respond(HttpStatusCode.Created, ticket)
        }

        // Restricted: trace a ticket back to its CLIP payment status/record.
        get("/{id}/payment") {
            requireAdminKey(call)
            val id = call.parameters["id"]
                ?: throw BadRequestException("Missing ticket id")
            val ticket = ticketService.getById(id)
            val paymentId = ticket.clipPaymentId
                ?: throw NotFoundException("Ticket '$id' was not paid via CLIP")
            call.respond(clipPaymentRepository.requireById(paymentId))
        }

        // Restricted: cancel a ticket
        delete("/{id}") {
            requireAdminKey(call)
            val id = call.parameters["id"]
                ?: throw BadRequestException("Missing ticket id")
            val cancelled = ticketService.cancel(id)
            call.respond(HttpStatusCode.OK, cancelled)
        }
    }
}
