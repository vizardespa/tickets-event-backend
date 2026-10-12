package com.tickets.services

/**
 * Thrown when a requested resource cannot be found.
 */
class NotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when a request is invalid (e.g. no seats available, bad seat number).
 */
class BadRequestException(message: String) : RuntimeException(message)

/**
 * Thrown when the call to the CLIP payments API fails or CLIP returns an error response.
 */
class ClipPaymentException(message: String) : RuntimeException(message)

/**
 * Thrown when a caller is not allowed to perform a restricted action (e.g. manual ticket creation).
 */
class ForbiddenException(message: String) : RuntimeException(message)

/**
 * Thrown when a CLIP-paid ticket purchase is attempted outside its ticket type's purchase window.
 * Manual ticket creation never triggers this exception.
 */
class TicketPurchaseWindowException(message: String) : RuntimeException(message)
