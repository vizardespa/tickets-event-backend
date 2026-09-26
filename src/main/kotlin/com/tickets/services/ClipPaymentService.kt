package com.tickets.services

import com.tickets.models.ClipApiCustomer
import com.tickets.models.ClipApiPaymentMethod
import com.tickets.models.ClipApiPaymentPayload
import com.tickets.models.ClipPaymentRequest
import com.tickets.models.ClipPaymentResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Calls CLIP's Checkout Transparente "POST /payments" endpoint.
 * See: https://developer.clip.mx/reference/realizar-el-pago-con-el-sdk-de-checkout-transparente
 *
 * TODO: replace [clipApiKey] with the real CLIP API key before going live.
 * It can also be supplied via the CLIP_API_KEY environment variable.
 */
class ClipPaymentService(
    private val baseUrl: String = "https://api.payclip.com",
    private val clipApiKey: String = System.getenv("CLIP_API_KEY") ?: "REPLACE_WITH_CLIP_API_KEY",
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
) {

    /**
     * Validates the incoming request, forwards it to CLIP, and returns the parsed response
     * so callers can decide on next steps based on `status` / `status_detail`.
     */
    suspend fun charge(request: ClipPaymentRequest): ClipPaymentResponse {
        validate(request)

        val payload = ClipApiPaymentPayload(
            amount = request.amount,
            currency = request.currency,
            description = request.description,
            paymentMethod = ClipApiPaymentMethod(token = request.cardToken),
            customer = ClipApiCustomer(email = request.customerEmail, phone = request.customerPhone)
        )

        val response: HttpResponse = try {
            client.post("$baseUrl/payments") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $clipApiKey")
                setBody(payload)
            }
        } catch (ex: Exception) {
            throw ClipPaymentException("Could not reach CLIP payments API: ${ex.message}")
        }

        if (!response.status.isSuccess()) {
            val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
            throw ClipPaymentException(
                "CLIP payments API returned ${response.status.value}: $errorBody"
            )
        }

        return try {
            response.body<ClipPaymentResponse>()
        } catch (ex: Exception) {
            throw ClipPaymentException("Could not parse CLIP payments API response: ${ex.message}")
        }
    }

    private fun validate(request: ClipPaymentRequest) {
        if (request.amount <= 0.0) {
            throw BadRequestException("amount must be greater than 0")
        }
        if (request.description.isBlank()) {
            throw BadRequestException("description is required")
        }
        if (request.cardToken.isBlank()) {
            throw BadRequestException("cardToken is required")
        }
        if (request.customerEmail.isBlank()) {
            throw BadRequestException("customerEmail is required")
        }
        if (request.customerPhone.isBlank()) {
            throw BadRequestException("customerPhone is required")
        }
    }

    fun close() {
        client.close()
    }
}
