package com.tickets.services

import com.tickets.db.ClipPaymentsTable
import com.tickets.models.ClipPayment
import com.tickets.models.ClipPaymentResponse
import com.tickets.models.toClipPayment
import java.time.Instant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * MySQL-backed store for our durable copy of CLIP payment data (see [ClipPayment]),
 * stored in [ClipPaymentsTable].
 *
 * This is populated every time we receive a [ClipPaymentResponse] from CLIP (on charge and on
 * status checks), so payment information remains retrievable for as long as we need it, even
 * after CLIP itself stops returning it.
 */
class ClipPaymentRepository {

    private fun ResultRow.toClipPayment() = ClipPayment(
        id = this[ClipPaymentsTable.id],
        ticketId = this[ClipPaymentsTable.ticketId],
        status = this[ClipPaymentsTable.status],
        statusDetailCode = this[ClipPaymentsTable.statusDetailCode],
        statusDetailMessage = this[ClipPaymentsTable.statusDetailMessage],
        amount = this[ClipPaymentsTable.amount],
        currency = this[ClipPaymentsTable.currency],
        description = this[ClipPaymentsTable.description],
        receiptNo = this[ClipPaymentsTable.receiptNo],
        clipCreatedAt = this[ClipPaymentsTable.clipCreatedAt],
        recordedAt = this[ClipPaymentsTable.recordedAt],
        updatedAt = this[ClipPaymentsTable.updatedAt]
    )

    private fun findById(id: String): ClipPayment? = ClipPaymentsTable.selectAll()
        .where { ClipPaymentsTable.id eq id }
        .map { it.toClipPayment() }
        .firstOrNull()

    /** Records or refreshes our copy of a CLIP payment response. */
    fun upsert(response: ClipPaymentResponse): ClipPayment = transaction {
        val id = response.id ?: throw ClipPaymentException("CLIP response is missing an id")
        val existing = findById(id)
        val record = response.toClipPayment(existing = existing)

        if (existing == null) {
            ClipPaymentsTable.insert {
                it[ClipPaymentsTable.id] = record.id
                it[ticketId] = record.ticketId
                it[status] = record.status
                it[statusDetailCode] = record.statusDetailCode
                it[statusDetailMessage] = record.statusDetailMessage
                it[amount] = record.amount
                it[currency] = record.currency
                it[description] = record.description
                it[receiptNo] = record.receiptNo
                it[clipCreatedAt] = record.clipCreatedAt
                it[recordedAt] = record.recordedAt
                it[updatedAt] = record.updatedAt
            }
        } else {
            ClipPaymentsTable.update({ ClipPaymentsTable.id eq id }) {
                it[status] = record.status
                it[statusDetailCode] = record.statusDetailCode
                it[statusDetailMessage] = record.statusDetailMessage
                it[amount] = record.amount
                it[currency] = record.currency
                it[description] = record.description
                it[receiptNo] = record.receiptNo
                it[clipCreatedAt] = record.clipCreatedAt
                it[updatedAt] = record.updatedAt
            }
        }
        record
    }

    fun getById(id: String): ClipPayment? = transaction { findById(id) }

    fun requireById(id: String): ClipPayment =
        getById(id) ?: throw NotFoundException("CLIP payment '$id' not found")

    /**
     * Links a CLIP payment to the ticket it paid for. Enforces a 1:1 relationship: a payment
     * already linked to a different ticket cannot be reused.
     */
    fun linkTicket(paymentId: String, ticketId: String): ClipPayment = transaction {
        val payment = findById(paymentId) ?: throw NotFoundException("CLIP payment '$paymentId' not found")
        if (payment.ticketId != null && payment.ticketId != ticketId) {
            throw BadRequestException(
                "CLIP payment '$paymentId' is already linked to ticket '${payment.ticketId}'"
            )
        }
        val updatedAt = Instant.now().toString()
        ClipPaymentsTable.update({ ClipPaymentsTable.id eq paymentId }) {
            it[ClipPaymentsTable.ticketId] = ticketId
            it[ClipPaymentsTable.updatedAt] = updatedAt
        }
        payment.copy(ticketId = ticketId, updatedAt = updatedAt)
    }
}
