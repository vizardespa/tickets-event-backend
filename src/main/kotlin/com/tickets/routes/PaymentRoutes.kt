package com.tickets.routes

import com.tickets.models.ClipPaymentRequest
import com.tickets.services.ClipPaymentService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
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
    }
}
