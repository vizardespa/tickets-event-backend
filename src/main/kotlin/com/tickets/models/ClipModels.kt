package com.tickets.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload our API receives to trigger a CLIP "Checkout Transparente" payment.
 * `cardToken` is the token previously generated on the client-side via the CLIP SDK.
 */
@Serializable
data class ClipPaymentRequest(
    val amount: Double,
    val currency: String = "MXN",
    val description: String,
    val cardToken: String,
    val customerEmail: String,
    val customerPhone: String
)

/**
 * Body sent to CLIP's `POST /payments` endpoint.
 * Mirrors https://developer.clip.mx/reference/realizar-el-pago-con-el-sdk-de-checkout-transparente
 */
@Serializable
data class ClipApiPaymentPayload(
    val amount: Double,
    val currency: String,
    val description: String,
    @SerialName("payment_method") val paymentMethod: ClipApiPaymentMethod,
    val customer: ClipApiCustomer
)

@Serializable
data class ClipApiPaymentMethod(
    val token: String
)

@Serializable
data class ClipApiCustomer(
    val email: String,
    val phone: String
)

/**
 * Subset of CLIP's payment response that callers need to decide next steps.
 * Extra fields returned by CLIP are ignored (ignoreUnknownKeys = true).
 */
@Serializable
data class ClipPaymentResponse(
    val id: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val description: String? = null,
    val status: String? = null,
    @SerialName("status_detail") val statusDetail: ClipStatusDetail? = null,
    @SerialName("receipt_no") val receiptNo: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("pending_action") val pendingAction: ClipPendingAction? = null
)

@Serializable
data class ClipStatusDetail(
    val code: String? = null,
    val message: String? = null
)

/**
 * Present when CLIP requires additional client-side action (e.g. 3DS authentication).
 * See: https://developer.clip.mx/reference/autenticacion-3ds-sdk
 */
@Serializable
data class ClipPendingAction(
    val url: String? = null,
    val type: String? = null
)

/**
 * Our durable copy of the relevant fields from CLIP's payment response.
 *
 * CLIP does not retain payment data indefinitely, so this record is persisted as soon as we
 * see a [ClipPaymentResponse] (on charge and on status checks), independent of whether a
 * ticket ends up being created. It lets any ticket be traced back to its payment status for
 * as long as needed (at least until the event is held).
 */
@Serializable
data class ClipPayment(
    val id: String,
    val ticketId: String? = null,
    val status: String? = null,
    val statusDetailCode: String? = null,
    val statusDetailMessage: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val description: String? = null,
    val receiptNo: String? = null,
    val clipCreatedAt: String? = null,
    val recordedAt: String,
    val updatedAt: String
)

/**
 * Maps a fresh [ClipPaymentResponse] into our durable [ClipPayment] record, preserving
 * [ClipPayment.ticketId] and [ClipPayment.recordedAt] from any [existing] record so repeated
 * status checks only refresh the mutable fields.
 */
fun ClipPaymentResponse.toClipPayment(existing: ClipPayment? = null): ClipPayment {
    val now = java.time.Instant.now().toString()
    val paymentId = id ?: existing?.id
        ?: throw IllegalArgumentException("CLIP response is missing an id")
    return ClipPayment(
        id = paymentId,
        ticketId = existing?.ticketId,
        status = status,
        statusDetailCode = statusDetail?.code,
        statusDetailMessage = statusDetail?.message,
        amount = amount,
        currency = currency,
        description = description,
        receiptNo = receiptNo,
        clipCreatedAt = createdAt,
        recordedAt = existing?.recordedAt ?: now,
        updatedAt = now
    )
}
