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
