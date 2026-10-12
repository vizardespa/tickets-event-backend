package com.tickets

import com.tickets.db.DatabaseFactory
import com.tickets.models.ErrorResponse
import com.tickets.models.HealthStatus
import com.tickets.routes.eventRoutes
import com.tickets.routes.paymentRoutes
import com.tickets.routes.ticketRoutes
import com.tickets.routes.ticketTypeRoutes
import com.tickets.services.BadRequestException
import com.tickets.services.ClipPaymentException
import com.tickets.services.ClipPaymentRepository
import com.tickets.services.ClipPaymentService
import com.tickets.services.EventService
import com.tickets.services.ForbiddenException
import com.tickets.services.NotFoundException
import com.tickets.services.TicketPurchaseWindowException
import com.tickets.services.TicketService
import com.tickets.services.TicketTypeService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
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
    // Database connection, schema and local-dev seed data
    DatabaseFactory.init()

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
        exception<ClipPaymentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse(error = "clip_payment_error", message = cause.message ?: "CLIP payment failed")
            )
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(error = "forbidden", message = cause.message ?: "Forbidden")
            )
        }
        exception<TicketPurchaseWindowException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "purchase_window_closed",
                    message = cause.message ?: "Ticket purchases are closed for this ticket type"
                )
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
    val clipPaymentRepository = ClipPaymentRepository()
    val ticketTypeService = TicketTypeService(eventService)
    val ticketService = TicketService(eventService, clipPaymentRepository, ticketTypeService)
    val clipPaymentService = ClipPaymentService()

    environment.monitor.subscribe(ApplicationStopped) {
        clipPaymentService.close()
    }

    routing {
        get("/health") {
            call.respond(HealthStatus(status = "ok"))
        }
        eventRoutes(eventService)
        ticketTypeRoutes(ticketTypeService)
        ticketRoutes(ticketService, clipPaymentRepository)
        paymentRoutes(clipPaymentService, clipPaymentRepository)
    }
}
