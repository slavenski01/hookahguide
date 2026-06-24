package com.hookahguide

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

data class Config(
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080,
    val host: String = System.getenv("HOST") ?: "0.0.0.0",
    val contentRoot: String = System.getenv("CONTENT_ROOT") ?: "..",
    val meiliUrl: String? = System.getenv("MEILI_URL"),
    val meiliKey: String? = System.getenv("MEILI_MASTER_KEY"),
)

fun main() {
    val config = Config()
    val log = LoggerFactory.getLogger("com.hookahguide.Application")

    val repo = ContentRepository(File(config.contentRoot).absoluteFile)
    repo.load()

    val search = SearchService(repo, config.meiliUrl, config.meiliKey)

    log.info("Запуск HookahGuide API на ${config.host}:${config.port} (CONTENT_ROOT=${File(config.contentRoot).absolutePath})")

    embeddedServer(CIO, port = config.port, host = config.host) {
        configure(repo, search)
        // Индексируем в Meili в фоне, чтобы не блокировать старт
        launch { search.indexAll() }
    }.start(wait = true)
}

fun Application.configure(repo: ContentRepository, search: SearchService) {
    install(ContentNegotiation) {
        json(Json { prettyPrint = false; encodeDefaults = true })
    }
    install(DefaultHeaders)
    install(Compression)
    install(CallLogging)
    install(CORS) {
        anyHost() // мобильное приложение; при необходимости ограничить allowHost(...)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            LoggerFactory.getLogger("StatusPages").error("Необработанная ошибка", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error"))
        }
    }
    registerRoutes(repo, search)
}

fun Application.registerRoutes(repo: ContentRepository, search: SearchService) = routing {

    get("/health") {
        call.respond(
            HealthResponse(
                status = "ok",
                articles = repo.articles.size,
                sections = repo.sections.size,
                searchEngine = search.engine,
            )
        )
    }

    route("/api") {

        get("/sections") { call.respond(repo.sections) }

        // Список статей с фильтрами: ?section=&level=&status=&tag=&limit=&offset=
        get("/articles") {
            val section = call.request.queryParameters["section"]
            val level = call.request.queryParameters["level"]
            val status = call.request.queryParameters["status"]
            val tag = call.request.queryParameters["tag"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
            val offset = call.request.queryParameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

            val filtered = repo.articles.asSequence()
                .filter { section == null || it.section == section }
                .filter { level == null || it.level == level }
                .filter { status == null || it.status == status }
                .filter { tag == null || it.tags.contains(tag) }
                .map { it.toMeta() }
                .toList()

            call.respond(filtered.drop(offset).take(limit))
        }

        // Полная статья (с телом Markdown)
        get("/articles/{slug}") {
            val slug = call.parameters["slug"]
            val article = slug?.let { repo.article(it) }
            if (article == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("article_not_found"))
            } else {
                call.respond(article)
            }
        }

        // Поиск: ?q=...&section=&limit=
        get("/search") {
            val q = call.request.queryParameters["q"].orEmpty()
            val section = call.request.queryParameters["section"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
            call.respond(search.search(q, section, limit))
        }

        // Справочные данные: brands, bowls, coals, mixes, glossary, tobacco-leaf, flavor-families, sections, articles
        get("/reference/{name}") {
            val name = call.parameters["name"]
            val data = name?.let { repo.reference(it) }
            if (data == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("reference_not_found: доступны ${repo.referenceNames.joinToString(", ")}")
                )
            } else {
                call.respond(data)
            }
        }
    }
}
