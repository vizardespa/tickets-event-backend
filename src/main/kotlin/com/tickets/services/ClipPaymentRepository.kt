package com.tickets.services

import com.tickets.models.ClipPayment
import com.tickets.models.ClipPaymentResponse
import com.tickets.models.toClipPayment
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store for our durable copy of CLIP payment data (see [ClipPayment]).
 *
 * This is populated every time we receive a [ClipPaymentResponse] from CLIP (on charge and on
 * status checks), so payment information remains retrievable for as long as we need it, even
 * after CLIP itself stops returning it.
 */
class ClipPaymentRepository {

    private val payments = ConcurrentHashMap<String, ClipPayment>()

    /** Records or refreshes our copy of a CLIP payment response. */
    fun upsert(response: ClipPaymentResponse): ClipPayment {
        val id = response.id ?: throw ClipPaymentException("CLIP response is missing an id")
        val record = response.toClipPayment(existing = payments[id])
        payments[id] = record
        return record
    }

    fun getById(id: String): ClipPayment? = payments[id]

    fun requireById(id: String): ClipPayment =
        getById(id) ?: throw NotFoundException("CLIP payment '$id' not found")

    /**
     * Links a CLIP payment to the ticket it paid for. Enforces a 1:1 relationship: a payment
     * already linked to a different ticket cannot be reused.
     */
    fun linkTicket(paymentId: String, ticketId: String): ClipPayment {
        return payments.compute(paymentId) { _, current ->
            val payment = current ?: throw NotFoundException("CLIP payment '$paymentId' not found")
            if (payment.ticketId != null && payment.ticketId != ticketId) {
                throw BadRequestException(
                    "CLIP payment '$paymentId' is already linked to ticket '${payment.ticketId}'"
                )
            }
            payment.copy(ticketId = ticketId, updatedAt = Instant.now().toString())
        }!!
    }
}
