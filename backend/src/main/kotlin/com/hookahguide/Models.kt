package com.hookahguide

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Section(
    val id: String,
    val order: Int,
    val title: String,
    val slug: String,
    val description: String,
)

@Serializable
data class ArticleMeta(
    val slug: String,
    val title: String,
    val section: String,
    val level: String,
    val status: String,
    val tags: List<String>,
    val sources: Int,
    val path: String,
    val updated: String? = null,
)

@Serializable
data class Article(
    val slug: String,
    val title: String,
    val section: String,
    val level: String,
    val status: String,
    val tags: List<String>,
    val sources: Int,
    val path: String,
    val updated: String? = null,
    val body: String,
)

fun Article.toMeta() = ArticleMeta(slug, title, section, level, status, tags, sources, path, updated)

@Serializable
data class SearchHit(
    val slug: String,
    val title: String,
    val section: String,
    val level: String,
    val snippet: String? = null,
)

@Serializable
data class SearchResponse(
    val query: String,
    val engine: String,
    val total: Int,
    val hits: List<SearchHit>,
)

@Serializable
data class HealthResponse(
    val status: String,
    val articles: Int,
    val sections: Int,
    val searchEngine: String,
)

@Serializable
data class ErrorResponse(val error: String)

/** Справочные данные (json-файлы из data) отдаются как есть. */
@Serializable
data class ReferenceData(val name: String, val items: JsonElement)
