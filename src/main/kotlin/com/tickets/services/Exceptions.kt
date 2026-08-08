package com.tickets.services

/**
 * Thrown when a requested resource cannot be found.
 */
class NotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when a request is invalid (e.g. no seats available, bad seat number).
 */
class BadRequestException(message: String) : RuntimeException(message)
