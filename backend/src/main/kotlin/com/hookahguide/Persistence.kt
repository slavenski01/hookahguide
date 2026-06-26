package com.hookahguide

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Подключение к БД (Postgres в проде, H2 в локальной разработке/тестах) и схема.
 * JSON-поля хранятся как text для переносимости между Postgres и H2.
 */
object Persistence {
    private val log = LoggerFactory.getLogger(Persistence::class.java)

    fun init(url: String, user: String?, password: String?, attempts: Int = 30, delayMs: Long = 2000) {
        val cfg = HikariConfig().apply {
            jdbcUrl = url
            driverClassName = if (url.startsWith("jdbc:postgresql")) "org.postgresql.Driver" else "org.h2.Driver"
            if (!user.isNullOrBlank()) username = user
            if (!password.isNullOrBlank()) this.password = password
            maximumPoolSize = 5
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
        Database.connect(HikariDataSource(cfg))

        // БД (Postgres) может стартовать позже API в docker — ждём с ретраями.
        var lastError: Exception? = null
        repeat(attempts) { i ->
            try {
                transaction {
                    SchemaUtils.createMissingTablesAndColumns(
                        Users, Notes, EditRequests, UserMixes, UserPackings, UserHookahs,
                    )
                }
                log.info("БД подключена: ${url.substringBefore('?')}")
                return
            } catch (e: Exception) {
                lastError = e
                if (i == 0) log.info("Ожидание базы данных…")
                Thread.sleep(delayMs)
            }
        }
        throw IllegalStateException("Не удалось подключиться к БД за отведённое время", lastError)
    }
}

object Users : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 320).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val displayName = varchar("display_name", 100).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Notes : Table("notes") {
    val id = uuid("id")
    val userId = uuid("user_id").index()
    val title = varchar("title", 200)
    val body = text("body")
    val articleSlug = varchar("article_slug", 120).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object EditRequests : Table("edit_requests") {
    val id = uuid("id")
    val userId = uuid("user_id").index()
    val articleSlug = varchar("article_slug", 120)
    val message = text("message")
    val status = varchar("status", 20) // pending | approved | rejected
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object UserMixes : Table("user_mixes") {
    val id = uuid("id")
    val userId = uuid("user_id").index()
    val name = varchar("name", 200)
    val components = text("components") // JSON-массив [{flavor,family,share}]
    val profile = text("profile").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object UserPackings : Table("user_packings") {
    val id = uuid("id")
    val userId = uuid("user_id").index()
    val title = varchar("title", 200)
    val tobacco = text("tobacco").nullable()
    val bowl = varchar("bowl", 120).nullable()
    val method = varchar("method", 120).nullable()
    val heat = varchar("heat", 120).nullable()
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object UserHookahs : Table("user_hookahs") {
    val id = uuid("id")
    val userId = uuid("user_id").index()
    val name = varchar("name", 200)
    val shaft = varchar("shaft", 160).nullable()
    val bowl = varchar("bowl", 160).nullable()
    val hose = varchar("hose", 160).nullable()
    val heat = varchar("heat", 160).nullable()
    val liquid = varchar("liquid", 160).nullable()
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}
