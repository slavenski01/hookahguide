package com.hookahguide

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

class EmailTakenException : RuntimeException("email_taken")

/** CRUD пользователей и их контента. Все методы работают в транзакции. */
class UserService {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun now() = Instant.now()

    // ---------- Аутентификация ----------

    fun register(email: String, password: String, displayName: String?): UserDto = transaction {
        val normEmail = email.trim().lowercase()
        val exists = Users.selectAll().where { Users.email eq normEmail }.any()
        if (exists) throw EmailTakenException()
        val id = UUID.randomUUID()
        val ts = now()
        Users.insert {
            it[Users.id] = id
            it[Users.email] = normEmail
            it[passwordHash] = Security.hashPassword(password)
            it[Users.displayName] = displayName?.trim()?.ifBlank { null }
            it[createdAt] = ts
        }
        UserDto(id.toString(), normEmail, displayName?.trim()?.ifBlank { null }, ts.toString())
    }

    /** Возвращает (userDto, passwordHash) или null, если email не найден. */
    fun findForLogin(email: String): Pair<UserDto, String>? = transaction {
        Users.selectAll().where { Users.email eq email.trim().lowercase() }.singleOrNull()?.let {
            userDto(it) to it[Users.passwordHash]
        }
    }

    fun userById(userId: String): UserDto? = transaction {
        Users.selectAll().where { Users.id eq UUID.fromString(userId) }.singleOrNull()?.let { userDto(it) }
    }

    private fun userDto(r: ResultRow) =
        UserDto(r[Users.id].toString(), r[Users.email], r[Users.displayName], r[Users.createdAt].toString())

    // ---------- Заметки ----------

    fun listNotes(userId: String): List<NoteDto> = transaction {
        Notes.selectAll().where { Notes.userId eq UUID.fromString(userId) }
            .orderBy(Notes.updatedAt, SortOrder.DESC).map(::noteDto)
    }

    fun createNote(userId: String, input: NoteInput): NoteDto = transaction {
        val id = UUID.randomUUID(); val ts = now()
        Notes.insert {
            it[Notes.id] = id
            it[Notes.userId] = UUID.fromString(userId)
            it[title] = input.title
            it[body] = input.body
            it[articleSlug] = input.articleSlug
            it[createdAt] = ts; it[updatedAt] = ts
        }
        NoteDto(id.toString(), input.title, input.body, input.articleSlug, ts.toString(), ts.toString())
    }

    fun updateNote(userId: String, noteId: String, input: NoteInput): NoteDto? = transaction {
        val uid = UUID.fromString(userId); val nid = UUID.fromString(noteId)
        val updated = Notes.update({ (Notes.id eq nid) and (Notes.userId eq uid) }) {
            it[title] = input.title; it[body] = input.body
            it[articleSlug] = input.articleSlug; it[updatedAt] = now()
        }
        if (updated == 0) null
        else Notes.selectAll().where { Notes.id eq nid }.single().let(::noteDto)
    }

    fun deleteNote(userId: String, noteId: String): Boolean = transaction {
        Notes.deleteWhere { Op.build { (Notes.id eq UUID.fromString(noteId)) and (Notes.userId eq UUID.fromString(userId)) } } > 0
    }

    private fun noteDto(r: ResultRow) = NoteDto(
        r[Notes.id].toString(), r[Notes.title], r[Notes.body], r[Notes.articleSlug],
        r[Notes.createdAt].toString(), r[Notes.updatedAt].toString(),
    )

    // ---------- Заявки на правку ----------

    fun listEditRequests(userId: String): List<EditRequestDto> = transaction {
        EditRequests.selectAll().where { EditRequests.userId eq UUID.fromString(userId) }
            .orderBy(EditRequests.createdAt, SortOrder.DESC).map(::editDto)
    }

    fun createEditRequest(userId: String, input: EditRequestInput): EditRequestDto = transaction {
        val id = UUID.randomUUID(); val ts = now()
        EditRequests.insert {
            it[EditRequests.id] = id
            it[EditRequests.userId] = UUID.fromString(userId)
            it[articleSlug] = input.articleSlug
            it[message] = input.message
            it[status] = "pending"
            it[createdAt] = ts
        }
        EditRequestDto(id.toString(), input.articleSlug, input.message, "pending", ts.toString())
    }

    fun deleteEditRequest(userId: String, reqId: String): Boolean = transaction {
        EditRequests.deleteWhere {
            Op.build { (EditRequests.id eq UUID.fromString(reqId)) and (EditRequests.userId eq UUID.fromString(userId)) }
        } > 0
    }

    private fun editDto(r: ResultRow) = EditRequestDto(
        r[EditRequests.id].toString(), r[EditRequests.articleSlug], r[EditRequests.message],
        r[EditRequests.status], r[EditRequests.createdAt].toString(),
    )

    // ---------- Свои миксы ----------

    fun listMixes(userId: String): List<UserMixDto> = transaction {
        UserMixes.selectAll().where { UserMixes.userId eq UUID.fromString(userId) }
            .orderBy(UserMixes.updatedAt, SortOrder.DESC).map(::mixDto)
    }

    fun createMix(userId: String, input: MixInput): UserMixDto = transaction {
        val id = UUID.randomUUID(); val ts = now()
        UserMixes.insert {
            it[UserMixes.id] = id
            it[UserMixes.userId] = UUID.fromString(userId)
            it[name] = input.name
            it[components] = json.encodeToString(input.components)
            it[profile] = input.profile
            it[createdAt] = ts; it[updatedAt] = ts
        }
        UserMixDto(id.toString(), input.name, input.components, input.profile, ts.toString(), ts.toString())
    }

    fun updateMix(userId: String, mixId: String, input: MixInput): UserMixDto? = transaction {
        val uid = UUID.fromString(userId); val mid = UUID.fromString(mixId)
        val updated = UserMixes.update({ (UserMixes.id eq mid) and (UserMixes.userId eq uid) }) {
            it[name] = input.name
            it[components] = json.encodeToString(input.components)
            it[profile] = input.profile; it[updatedAt] = now()
        }
        if (updated == 0) null else UserMixes.selectAll().where { UserMixes.id eq mid }.single().let(::mixDto)
    }

    fun deleteMix(userId: String, mixId: String): Boolean = transaction {
        UserMixes.deleteWhere { Op.build { (UserMixes.id eq UUID.fromString(mixId)) and (UserMixes.userId eq UUID.fromString(userId)) } } > 0
    }

    private fun mixDto(r: ResultRow): UserMixDto {
        val comps = runCatching { json.decodeFromString<List<MixComponentDto>>(r[UserMixes.components]) }.getOrDefault(emptyList())
        return UserMixDto(r[UserMixes.id].toString(), r[UserMixes.name], comps, r[UserMixes.profile],
            r[UserMixes.createdAt].toString(), r[UserMixes.updatedAt].toString())
    }

    // ---------- Свои забивки ----------

    fun listPackings(userId: String): List<PackingDto> = transaction {
        UserPackings.selectAll().where { UserPackings.userId eq UUID.fromString(userId) }
            .orderBy(UserPackings.updatedAt, SortOrder.DESC).map(::packingDto)
    }

    fun createPacking(userId: String, input: PackingInput): PackingDto = transaction {
        val id = UUID.randomUUID(); val ts = now()
        UserPackings.insert {
            it[UserPackings.id] = id
            it[UserPackings.userId] = UUID.fromString(userId)
            it[title] = input.title; it[tobacco] = input.tobacco; it[bowl] = input.bowl
            it[method] = input.method; it[heat] = input.heat; it[notes] = input.notes
            it[createdAt] = ts; it[updatedAt] = ts
        }
        packingFromInput(id.toString(), input, ts.toString())
    }

    fun updatePacking(userId: String, packId: String, input: PackingInput): PackingDto? = transaction {
        val uid = UUID.fromString(userId); val pid = UUID.fromString(packId)
        val updated = UserPackings.update({ (UserPackings.id eq pid) and (UserPackings.userId eq uid) }) {
            it[title] = input.title; it[tobacco] = input.tobacco; it[bowl] = input.bowl
            it[method] = input.method; it[heat] = input.heat; it[notes] = input.notes; it[updatedAt] = now()
        }
        if (updated == 0) null else UserPackings.selectAll().where { UserPackings.id eq pid }.single().let(::packingDto)
    }

    fun deletePacking(userId: String, packId: String): Boolean = transaction {
        UserPackings.deleteWhere { Op.build { (UserPackings.id eq UUID.fromString(packId)) and (UserPackings.userId eq UUID.fromString(userId)) } } > 0
    }

    private fun packingFromInput(id: String, i: PackingInput, ts: String) =
        PackingDto(id, i.title, i.tobacco, i.bowl, i.method, i.heat, i.notes, ts, ts)

    private fun packingDto(r: ResultRow) = PackingDto(
        r[UserPackings.id].toString(), r[UserPackings.title], r[UserPackings.tobacco], r[UserPackings.bowl],
        r[UserPackings.method], r[UserPackings.heat], r[UserPackings.notes],
        r[UserPackings.createdAt].toString(), r[UserPackings.updatedAt].toString(),
    )

    // ---------- Свои кальяны ----------

    fun listHookahs(userId: String): List<HookahDto> = transaction {
        UserHookahs.selectAll().where { UserHookahs.userId eq UUID.fromString(userId) }
            .orderBy(UserHookahs.updatedAt, SortOrder.DESC).map(::hookahDto)
    }

    fun createHookah(userId: String, input: HookahInput): HookahDto = transaction {
        val id = UUID.randomUUID(); val ts = now()
        UserHookahs.insert {
            it[UserHookahs.id] = id
            it[UserHookahs.userId] = UUID.fromString(userId)
            it[name] = input.name; it[shaft] = input.shaft; it[bowl] = input.bowl; it[hose] = input.hose
            it[heat] = input.heat; it[liquid] = input.liquid; it[notes] = input.notes
            it[createdAt] = ts; it[updatedAt] = ts
        }
        HookahDto(id.toString(), input.name, input.shaft, input.bowl, input.hose, input.heat, input.liquid, input.notes, ts.toString(), ts.toString())
    }

    fun updateHookah(userId: String, hookahId: String, input: HookahInput): HookahDto? = transaction {
        val uid = UUID.fromString(userId); val hid = UUID.fromString(hookahId)
        val updated = UserHookahs.update({ (UserHookahs.id eq hid) and (UserHookahs.userId eq uid) }) {
            it[name] = input.name; it[shaft] = input.shaft; it[bowl] = input.bowl; it[hose] = input.hose
            it[heat] = input.heat; it[liquid] = input.liquid; it[notes] = input.notes; it[updatedAt] = now()
        }
        if (updated == 0) null else UserHookahs.selectAll().where { UserHookahs.id eq hid }.single().let(::hookahDto)
    }

    fun deleteHookah(userId: String, hookahId: String): Boolean = transaction {
        UserHookahs.deleteWhere { Op.build { (UserHookahs.id eq UUID.fromString(hookahId)) and (UserHookahs.userId eq UUID.fromString(userId)) } } > 0
    }

    private fun hookahDto(r: ResultRow) = HookahDto(
        r[UserHookahs.id].toString(), r[UserHookahs.name], r[UserHookahs.shaft], r[UserHookahs.bowl],
        r[UserHookahs.hose], r[UserHookahs.heat], r[UserHookahs.liquid], r[UserHookahs.notes],
        r[UserHookahs.createdAt].toString(), r[UserHookahs.updatedAt].toString(),
    )
}
