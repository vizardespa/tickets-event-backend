package com.tickets.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Sets up the MySQL connection pool, Exposed binding and schema for the application,
 * and seeds one sample event for local development when the database is empty.
 */
object DatabaseFactory {

    fun init() {
        val host = System.getenv("MYSQL_HOST") ?: "localhost"
        val port = System.getenv("MYSQL_PORT") ?: "3306"
        val name = System.getenv("MYSQL_DATABASE") ?: "tickets"
        val user = System.getenv("MYSQL_USER") ?: "tickets"
        val password = System.getenv("MYSQL_PASSWORD") ?: "tickets"

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://$host:$port/$name?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            driverClassName = "com.mysql.cj.jdbc.Driver"
            username = user
            this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }
        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.createMissingTablesAndColumns(EventsTable, TicketTypesTable, ClipPaymentsTable, TicketsTable)
            val eventId = seedSampleEventIfEmpty()
            seedSampleTicketTypesIfEmpty(eventId)
        }
    }

    /** Inserts the sample event if the database has none yet. Returns its id either way. */
    private fun seedSampleEventIfEmpty(): String {
        val existing = EventsTable.selectAll().where { EventsTable.name eq "Tijuana Rockea" }
            .map { it[EventsTable.id] }
            .firstOrNull()
        if (existing != null) return existing

        val eventId = UUID.randomUUID().toString()
        EventsTable.insert {
            it[id] = eventId
            it[name] = "Tijuana Rockea"
            it[description] = "A night of live rock music in Tijuana."
            it[venue] = "Foro Tijuana Rockea"
            it[date] = "2026-06-20T21:00:00Z"
            it[totalSeats] = 300
            it[availableSeats] = 300
            it[createdAt] = Instant.now().toString()
        }
        return eventId
    }

    /** Inserts the sample ticket types for the given event if it has none yet. */
    private fun seedSampleTicketTypesIfEmpty(eventId: String) {
        val hasTicketTypes = TicketTypesTable.selectAll().where { TicketTypesTable.eventId eq eventId }
            .limit(1).any()
        if (hasTicketTypes) return

        val now = Instant.now().toString()

        data class SeedTicketType(
            val name: String,
            val description: String,
            val price: Double,
            val purchaseStartAt: String,
            val purchaseEndAt: String
        )

        val ticketTypes = listOf(
            SeedTicketType(
                "General_Comunidad", "Boleto General Comunidad", 1350.0,
                "2026-11-16T00:00:00Z", "2026-12-31T23:59:59Z"
            ),
            SeedTicketType(
                "General_Tijuana", "Boleto General Fase Tijuana", 1750.0,
                "2027-01-01T00:00:00Z", "2027-02-28T23:59:59Z"
            ),
            SeedTicketType(
                "General_Rockea", "Boleto General Fase Rockea", 2150.0,
                "2027-03-01T00:00:00Z", "2027-04-30T23:59:59Z"
            ),
            SeedTicketType(
                "General_Frontera", "Boleto General Fase Frontera", 2550.0,
                "2027-05-01T00:00:00Z", "2027-06-30T23:59:59Z"
            ),
            SeedTicketType(
                "General_Julio", "Boleto General Mes Julio", 2950.0,
                "2027-07-01T00:00:00Z", "2027-07-16T23:59:59Z"
            )
        )

        ticketTypes.forEach { seed ->
            TicketTypesTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[this.eventId] = eventId
                it[name] = seed.name
                it[description] = seed.description
                it[price] = seed.price
                it[purchaseStartAt] = seed.purchaseStartAt
                it[purchaseEndAt] = seed.purchaseEndAt
                it[createdAt] = now
            }
        }
    }
}
