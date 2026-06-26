package com.hookahguide

import kotlinx.serialization.Serializable

// --- Аутентификация ---

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String? = null)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class UserDto(val id: String, val email: String, val displayName: String?, val createdAt: String)

@Serializable
data class AuthResponse(val token: String, val user: UserDto)

// --- Заметки ---

@Serializable
data class NoteInput(val title: String, val body: String, val articleSlug: String? = null)

@Serializable
data class NoteDto(
    val id: String, val title: String, val body: String, val articleSlug: String?,
    val createdAt: String, val updatedAt: String,
)

// --- Заявки на правку статьи ---

@Serializable
data class EditRequestInput(val articleSlug: String, val message: String)

@Serializable
data class EditRequestDto(
    val id: String, val articleSlug: String, val message: String, val status: String, val createdAt: String,
)

// --- Свои миксы ---

@Serializable
data class MixComponentDto(val flavor: String, val family: String? = null, val share: Double? = null)

@Serializable
data class MixInput(val name: String, val components: List<MixComponentDto> = emptyList(), val profile: String? = null)

@Serializable
data class UserMixDto(
    val id: String, val name: String, val components: List<MixComponentDto>, val profile: String?,
    val createdAt: String, val updatedAt: String,
)

// --- Свои забивки ---

@Serializable
data class PackingInput(
    val title: String, val tobacco: String? = null, val bowl: String? = null,
    val method: String? = null, val heat: String? = null, val notes: String? = null,
)

@Serializable
data class PackingDto(
    val id: String, val title: String, val tobacco: String?, val bowl: String?,
    val method: String?, val heat: String?, val notes: String?,
    val createdAt: String, val updatedAt: String,
)

// --- Свои варианты кальянов ---

@Serializable
data class HookahInput(
    val name: String, val shaft: String? = null, val bowl: String? = null, val hose: String? = null,
    val heat: String? = null, val liquid: String? = null, val notes: String? = null,
)

@Serializable
data class HookahDto(
    val id: String, val name: String, val shaft: String?, val bowl: String?, val hose: String?,
    val heat: String?, val liquid: String?, val notes: String?,
    val createdAt: String, val updatedAt: String,
)
