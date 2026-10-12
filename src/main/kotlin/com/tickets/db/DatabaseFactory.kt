package com.tickets.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
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
            SchemaUtils.create(EventsTable, ClipPaymentsTable, TicketsTable)
            seedSampleEventIfEmpty()
        }
    }

    private fun seedSampleEventIfEmpty() {
        val hasEvents = EventsTable.selectAll().limit(1).any()
        if (hasEvents) return

        val now = Instant.now().toString()
        EventsTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[name] = "Tijuana Rockea"
            it[description] = "A night of live rock music in Tijuana."
            it[venue] = "Foro Tijuana Rockea"
            it[date] = "2026-06-20T21:00:00Z"
            it[totalSeats] = 300
            it[availableSeats] = 300
            it[price] = 350.0
            it[createdAt] = now
        }
    }
}
