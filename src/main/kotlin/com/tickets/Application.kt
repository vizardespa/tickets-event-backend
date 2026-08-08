package com.tickets

import com.tickets.models.ErrorResponse
import com.tickets.models.HealthStatus
import com.tickets.routes.eventRoutes
import com.tickets.routes.ticketRoutes
import com.tickets.services.BadRequestException
import com.tickets.services.EventService
import com.tickets.services.NotFoundException
import com.tickets.services.TicketService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // JSON content negotiation
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(CallLogging)

    // CORS support
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    // Centralized error handling
    install(StatusPages) {
        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(error = "not_found", message = cause.message ?: "Resource not found")
            )
        }
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "bad_request", message = cause.message ?: "Bad request")
            )
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(error = "internal_error", message = cause.message ?: "Unexpected error")
            )
        }
    }

    // Dependency wiring (in-memory services)
    val eventService = EventService()
    val ticketService = TicketService(eventService)

    routing {
        get("/health") {
            call.respond(HealthStatus(status = "ok"))
        }
        eventRoutes(eventService)
        ticketRoutes(ticketService)
    }
}
