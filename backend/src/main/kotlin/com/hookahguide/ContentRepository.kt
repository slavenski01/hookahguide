package com.hookahguide

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Загружает базу знаний из файловой системы:
 *  - md-файлы из knowledge  → статьи (frontmatter + тело)
 *  - json-файлы из data     → справочники (отдаются как есть)
 *
 * contentRoot указывает на корень репозитория (где лежат knowledge и data).
 */
class ContentRepository(private val contentRoot: File) {

    private val log = LoggerFactory.getLogger(ContentRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    var sections: List<Section> = emptyList()
        private set

    @Volatile
    var articles: List<Article> = emptyList()
        private set

    private val articlesBySlug: MutableMap<String, Article> = HashMap()

    /** Имена доступных справочников из data/. */
    val referenceNames: List<String> = listOf(
        "sections", "tobacco-leaf", "brands", "bowls", "coals",
        "flavor-families", "mixes", "glossary", "articles",
    )

    fun article(slug: String): Article? = articlesBySlug[slug]

    fun reference(name: String): JsonElement? {
        if (name !in referenceNames) return null
        val file = File(contentRoot, "data/$name.json")
        if (!file.isFile) return null
        return runCatching { json.parseToJsonElement(file.readText()) }
            .onFailure { log.warn("Не удалось разобрать data/$name.json: ${it.message}") }
            .getOrNull()
    }

    fun load() {
        val knowledgeDir = File(contentRoot, "knowledge")
        require(knowledgeDir.isDirectory) {
            "Не найдена папка knowledge/ в CONTENT_ROOT=${contentRoot.absolutePath}"
        }

        sections = loadSections()

        val loaded = knowledgeDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .filter { !(it.name == "README.md" && !it.path.contains("12-glossariy")) }
            .filter { it.name != "_template.md" }
            .mapNotNull { parseArticle(it) }
            .sortedBy { it.path }
            .toList()

        articlesBySlug.clear()
        loaded.forEach { articlesBySlug[it.slug] = it }
        articles = loaded
        log.info("Загружено: ${sections.size} разделов, ${articles.size} статей")
    }

    private fun loadSections(): List<Section> {
        val file = File(contentRoot, "data/sections.json")
        if (!file.isFile) return emptyList()
        return runCatching { json.decodeFromString<List<Section>>(file.readText()) }
            .onFailure { log.warn("sections.json: ${it.message}") }
            .getOrDefault(emptyList())
    }

    private fun parseArticle(file: File): Article? {
        val text = file.readText()
        val fm = parseFrontmatter(text) ?: return null
        val body = stripFrontmatter(text)
        val relPath = "knowledge/" + file.relativeTo(File(contentRoot, "knowledge")).path
        val slug = fm.scalar("slug") ?: file.nameWithoutExtension
        val section = fm.scalar("category") ?: relPath.split("/").getOrElse(1) { "" }
        return Article(
            slug = slug,
            title = fm.scalar("title") ?: slug,
            section = section,
            level = fm.scalar("level") ?: "beginner",
            status = fm.scalar("status") ?: "draft",
            tags = fm.list("tags"),
            sources = fm.list("sources").size,
            path = relPath,
            updated = fm.scalar("updated"),
            body = body,
        )
    }

    // --- Минимальный парсер frontmatter (YAML-подмножество, как в scripts/build-index.mjs) ---

    private class Frontmatter(val scalars: Map<String, String>, val lists: Map<String, List<String>>) {
        fun scalar(k: String): String? = scalars[k]
        fun list(k: String): List<String> = lists[k] ?: emptyList()
    }

    private fun parseFrontmatter(text: String): Frontmatter? {
        if (!text.startsWith("---")) return null
        val end = text.indexOf("\n---", 3)
        if (end < 0) return null
        val block = text.substring(text.indexOf('\n') + 1, end)
        val scalars = HashMap<String, String>()
        val lists = HashMap<String, MutableList<String>>()
        var currentKey: String? = null
        for (raw in block.lines()) {
            val listItem = Regex("^\\s*-\\s+(.*)$").find(raw)
            if (listItem != null && currentKey != null) {
                lists.getOrPut(currentKey!!) { mutableListOf() }.add(listItem.groupValues[1].trim())
                continue
            }
            val kv = Regex("^([A-Za-z_]+):\\s*(.*)$").find(raw) ?: continue
            val key = kv.groupValues[1]
            val value = kv.groupValues[2].trim()
            currentKey = key
            when {
                value.isEmpty() -> lists.getOrPut(key) { mutableListOf() }
                value == "[]" -> lists.getOrPut(key) { mutableListOf() }
                value.startsWith("[") && value.endsWith("]") ->
                    lists[key] = value.substring(1, value.length - 1)
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                else -> scalars[key] = value
            }
        }
        return Frontmatter(scalars, lists)
    }

    private fun stripFrontmatter(text: String): String {
        if (!text.startsWith("---")) return text.trim()
        val end = text.indexOf("\n---", 3)
        if (end < 0) return text.trim()
        val after = text.indexOf('\n', end + 1)
        return if (after < 0) "" else text.substring(after + 1).trim()
    }
}
