package com.tickets.routes

import com.tickets.services.ForbiddenException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/**
 * Shared secret for staff-only endpoints (e.g. manual/cash ticket creation).
 * Supplied via the ADMIN_API_KEY environment variable, mirroring the CLIP_API_KEY convention.
 *
 * TODO: replace with a real auth mechanism (e.g. ktor-server-auth) if stronger access control
 * is needed; this is the minimal fit since no auth plugin exists in this project today.
 */
private val adminApiKey: String = System.getenv("ADMIN_API_KEY") ?: "REPLACE_WITH_ADMIN_API_KEY"

/** Guards a restricted route behind the `X-Admin-Key` header. */
fun requireAdminKey(call: ApplicationCall) {
    val provided = call.request.header("X-Admin-Key")
    if (provided.isNullOrBlank() || provided != adminApiKey) {
        throw ForbiddenException("Missing or invalid admin key")
    }
}
