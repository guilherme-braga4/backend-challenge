package com.trace.payments.infrastructure.config

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object FrameworkConfig {

    fun init(application: Application) {
        initDatabase()
        initContentNegotiation(application)
    }

    private fun initDatabase() {
        val dbUrl = System.getenv("DATABASE_JDBC_URL")
            ?: System.getProperty("test.database.jdbc.url")
            ?: buildJdbcUrl(
                System.getenv("DB_HOST") ?: "localhost",
                System.getenv("DB_PORT")?.toIntOrNull() ?: 5432,
                System.getenv("DB_NAME") ?: "postgres"
            )
        val user = System.getenv("DB_USER") ?: System.getProperty("test.database.user") ?: "postgres"
        val password = System.getenv("DB_PASSWORD") ?: System.getProperty("test.database.password") ?: "postgres"

        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = dbUrl
            username = user
            this.password = password
            maximumPoolSize = 10
        })

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        Database.connect(ds)
    }

    private fun initContentNegotiation(application: Application) {
        application.install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }
    }

    private fun buildJdbcUrl(host: String, port: Int, name: String): String =
        "jdbc:postgresql://$host:$port/$name"
}
