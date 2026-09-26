package com.tickets.routes

import com.tickets.models.ClipPaymentRequest
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

fun Route.paymentRoutes(clipPaymentService: ClipPaymentService) {
    route("/api/payments") {

        // Charge a card via CLIP's Checkout Transparente (POST /Payments).
        post("/clip") {
            val request = call.receive<ClipPaymentRequest>()
            val result = clipPaymentService.charge(request)
            call.respond(HttpStatusCode.OK, result)
        }

        // Check the final status of a payment, e.g. after a 3DS authentication flow.
        // See: https://developer.clip.mx/reference/autenticacion-3ds-sdk
        get("/{id}") {
            val id = call.parameters["id"] ?: throw BadRequestException("Missing payment id")
            val result = clipPaymentService.getStatus(id)
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
