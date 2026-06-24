package com.hookahguide

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Поиск по статьям. Основной движок — Meilisearch (REST API).
 * Если Meili недоступен — прозрачный fallback на встроенный поиск по памяти,
 * чтобы API оставался работоспособным (локальная разработка, сбой Meili).
 */
class SearchService(
    private val repo: ContentRepository,
    private val meiliUrl: String?,
    private val meiliKey: String?,
    private val indexUid: String = "articles",
) {
    private val log = LoggerFactory.getLogger(SearchService::class.java)
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    var meiliReady: Boolean = false
        private set

    val engine: String get() = if (meiliReady) "meilisearch" else "builtin"

    private fun sectionTitle(id: String): String =
        repo.sections.firstOrNull { it.id == id }?.title ?: id

    private fun HttpRequestBuilder.auth() {
        if (!meiliKey.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $meiliKey")
    }

    /** Ждёт готовности Meili (он может стартовать позже API в compose). */
    private suspend fun waitForMeili(attempts: Int = 30, delayMs: Long = 2000): Boolean {
        repeat(attempts) { i ->
            val ok = runCatching {
                client.get("$meiliUrl/health") { auth() }.status.isSuccess()
            }.getOrDefault(false)
            if (ok) return true
            if (i == 0) log.info("Ожидание Meilisearch по адресу $meiliUrl …")
            delay(delayMs)
        }
        return false
    }

    /** Индексирует статьи в Meili. Безопасно вызывать при старте; ошибки не фатальны. */
    suspend fun indexAll() {
        if (meiliUrl.isNullOrBlank()) {
            log.info("MEILI_URL не задан — используется встроенный поиск")
            return
        }
        if (!waitForMeili()) {
            log.warn("Meilisearch не ответил за отведённое время — используется встроенный поиск")
            return
        }
        try {
            // 1. Индекс с первичным ключом slug
            client.post("$meiliUrl/indexes") {
                auth(); contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("uid", indexUid); put("primaryKey", "slug")
                }.toString())
            }
            // 2. Настройки поиска
            client.patch("$meiliUrl/indexes/$indexUid/settings") {
                auth(); contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    putJsonArray("searchableAttributes") {
                        add("title"); add("tags"); add("sectionTitle"); add("body")
                    }
                    putJsonArray("filterableAttributes") {
                        add("section"); add("level"); add("status"); add("tags")
                    }
                }.toString())
            }
            // 3. Документы
            val docs = buildJsonArray {
                for (a in repo.articles) add(articleDoc(a))
            }
            val resp = client.post("$meiliUrl/indexes/$indexUid/documents?primaryKey=slug") {
                auth(); contentType(ContentType.Application.Json); setBody(docs.toString())
            }
            if (resp.status.isSuccess()) {
                meiliReady = true
                log.info("Meilisearch проиндексирован: ${repo.articles.size} статей")
            } else {
                log.warn("Meili вернул ${resp.status} при индексации — fallback на встроенный поиск")
            }
        } catch (e: Exception) {
            log.warn("Meilisearch недоступен (${e.message}) — используется встроенный поиск")
            meiliReady = false
        }
    }

    private fun articleDoc(a: Article): JsonObject = buildJsonObject {
        put("slug", a.slug); put("title", a.title)
        put("section", a.section); put("sectionTitle", sectionTitle(a.section))
        put("level", a.level); put("status", a.status)
        putJsonArray("tags") { a.tags.forEach { add(it) } }
        put("body", a.body)
    }

    suspend fun search(query: String, section: String?, limit: Int): SearchResponse {
        if (query.isBlank()) return SearchResponse(query, engine, 0, emptyList())
        if (meiliReady) {
            runCatching { return meiliSearch(query, section, limit) }
                .onFailure { log.warn("Ошибка запроса к Meili (${it.message}) — fallback"); meiliReady = false }
        }
        return builtinSearch(query, section, limit)
    }

    private suspend fun meiliSearch(query: String, section: String?, limit: Int): SearchResponse {
        val body = buildJsonObject {
            put("q", query)
            put("limit", limit)
            putJsonArray("attributesToRetrieve") { add("slug"); add("title"); add("section"); add("level") }
            putJsonArray("attributesToCrop") { add("body") }
            put("cropLength", 30)
            putJsonArray("attributesToHighlight") { add("body") }
            if (!section.isNullOrBlank()) put("filter", "section = \"$section\"")
        }
        val resp = client.post("$meiliUrl/indexes/$indexUid/search") {
            auth(); contentType(ContentType.Application.Json); setBody(body.toString())
        }
        val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val hitsArr = obj["hits"]?.jsonArray ?: JsonArray(emptyList())
        val hits = hitsArr.map { it.jsonObject }.map { h ->
            val formatted = h["_formatted"]?.jsonObject
            val snippet = formatted?.get("body")?.jsonPrimitive?.contentOrNull
                ?.replace("\n", " ")?.trim()
            SearchHit(
                slug = h["slug"]!!.jsonPrimitive.content,
                title = h["title"]?.jsonPrimitive?.contentOrNull ?: "",
                section = h["section"]?.jsonPrimitive?.contentOrNull ?: "",
                level = h["level"]?.jsonPrimitive?.contentOrNull ?: "beginner",
                snippet = snippet,
            )
        }
        val total = obj["estimatedTotalHits"]?.jsonPrimitive?.intOrNull ?: hits.size
        return SearchResponse(query, "meilisearch", total, hits)
    }

    private fun builtinSearch(query: String, section: String?, limit: Int): SearchResponse {
        val terms = tokenize(query)
        if (terms.isEmpty()) return SearchResponse(query, "builtin", 0, emptyList())
        val scored = repo.articles
            .filter { section.isNullOrBlank() || it.section == section }
            .mapNotNull { a ->
                val title = a.title.lowercase()
                val tags = a.tags.joinToString(" ").lowercase()
                val body = a.body.lowercase()
                var score = 0
                for (t in terms) {
                    score += occurrences(title, t) * 5
                    score += occurrences(tags, t) * 3
                    score += occurrences(body, t)
                }
                if (score > 0) a to score else null
            }
            .sortedByDescending { it.second }
            .take(limit)
        val hits = scored.map { pair ->
            val a = pair.first
            SearchHit(a.slug, a.title, a.section, a.level, snippet(a.body, terms))
        }
        return SearchResponse(query, "builtin", hits.size, hits)
    }

    private fun tokenize(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.length >= 2 }

    private fun occurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0; var i = haystack.indexOf(needle)
        while (i >= 0) { count++; i = haystack.indexOf(needle, i + needle.length) }
        return count
    }

    private fun snippet(body: String, terms: List<String>): String? {
        val flat = body.replace("\n", " ").replace(Regex("\\s+"), " ")
        val lower = flat.lowercase()
        val idx = terms.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return flat.take(160)
        val start = (idx - 60).coerceAtLeast(0)
        val end = (idx + 100).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end).trim() + (if (end < flat.length) "…" else "")
    }

    fun close() = client.close()
}
