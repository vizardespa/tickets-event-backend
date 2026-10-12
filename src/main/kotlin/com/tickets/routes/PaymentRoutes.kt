package com.tickets.routes

import com.tickets.models.ClipPaymentRequest
import com.tickets.services.ClipPaymentRepository
import com.tickets.services.ClipPaymentService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import com.tickets.services.BadRequestException
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.paymentRoutes(clipPaymentService: ClipPaymentService, clipPaymentRepository: ClipPaymentRepository) {
    route("/api/payments") {

        // Charge a card via CLIP's Checkout Transparente (POST /Payments).
        post("/clip") {
            val request = call.receive<ClipPaymentRequest>()
            val result = clipPaymentService.charge(request)
            // Persist our durable copy immediately, regardless of whether a ticket is created.
            clipPaymentRepository.upsert(result)
            call.respond(HttpStatusCode.OK, result)
        }

        // Check the final status of a payment, e.g. after a 3DS authentication flow.
        // See: https://developer.clip.mx/reference/autenticacion-3ds-sdk
        get("/{id}") {
            val id = call.parameters["id"] ?: throw BadRequestException("Missing payment id")
            val result = clipPaymentService.getStatus(id)
            clipPaymentRepository.upsert(result)
            call.respond(HttpStatusCode.OK, result)
        }

        // Retrieve our stored copy of a payment without calling CLIP again. Since CLIP does not
        // retain payment data indefinitely, this is the source of truth until the event is held.
        get("/{id}/record") {
            val id = call.parameters["id"] ?: throw BadRequestException("Missing payment id")
            call.respond(clipPaymentRepository.requireById(id))
        }
    }
}
