package com.hookahguide

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("uid").asString()

private fun validEmail(s: String) = s.contains("@") && s.length in 3..320

/** Роуты аутентификации и пользовательского контента (/api/auth, /api/me). */
fun Route.userRoutes(service: UserService) {

    route("/api/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()
            if (!validEmail(req.email)) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_email"))
            if (req.password.length < 6) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("weak_password"))
            try {
                val user = service.register(req.email, req.password, req.displayName)
                val token = Security.makeToken(user.id, user.email, System.currentTimeMillis())
                call.respond(HttpStatusCode.Created, AuthResponse(token, user))
            } catch (e: EmailTakenException) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("email_taken"))
            }
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val found = service.findForLogin(req.email)
            if (found == null || !Security.verifyPassword(req.password, found.second)) {
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("invalid_credentials"))
            }
            val token = Security.makeToken(found.first.id, found.first.email, System.currentTimeMillis())
            call.respond(AuthResponse(token, found.first))
        }
    }

    authenticate("auth-jwt") {
        route("/api/me") {

            get { // текущий пользователь
                val user = service.userById(call.userId())
                if (user == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("user_not_found"))
                else call.respond(user)
            }

            // --- Заметки ---
            route("/notes") {
                get { call.respond(service.listNotes(call.userId())) }
                post { call.respond(HttpStatusCode.Created, service.createNote(call.userId(), call.receive())) }
                put("/{id}") {
                    val dto = service.updateNote(call.userId(), call.parameters["id"]!!, call.receive())
                    if (dto == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found")) else call.respond(dto)
                }
                delete("/{id}") {
                    if (service.deleteNote(call.userId(), call.parameters["id"]!!)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
                }
            }

            // --- Заявки на правку статьи ---
            route("/edit-requests") {
                get { call.respond(service.listEditRequests(call.userId())) }
                post { call.respond(HttpStatusCode.Created, service.createEditRequest(call.userId(), call.receive())) }
                delete("/{id}") {
                    if (service.deleteEditRequest(call.userId(), call.parameters["id"]!!)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
                }
            }

            // --- Свои миксы ---
            route("/mixes") {
                get { call.respond(service.listMixes(call.userId())) }
                post { call.respond(HttpStatusCode.Created, service.createMix(call.userId(), call.receive())) }
                put("/{id}") {
                    val dto = service.updateMix(call.userId(), call.parameters["id"]!!, call.receive())
                    if (dto == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found")) else call.respond(dto)
                }
                delete("/{id}") {
                    if (service.deleteMix(call.userId(), call.parameters["id"]!!)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
                }
            }

            // --- Свои забивки ---
            route("/packings") {
                get { call.respond(service.listPackings(call.userId())) }
                post { call.respond(HttpStatusCode.Created, service.createPacking(call.userId(), call.receive())) }
                put("/{id}") {
                    val dto = service.updatePacking(call.userId(), call.parameters["id"]!!, call.receive())
                    if (dto == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found")) else call.respond(dto)
                }
                delete("/{id}") {
                    if (service.deletePacking(call.userId(), call.parameters["id"]!!)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
                }
            }

            // --- Свои варианты кальянов ---
            route("/hookahs") {
                get { call.respond(service.listHookahs(call.userId())) }
                post { call.respond(HttpStatusCode.Created, service.createHookah(call.userId(), call.receive())) }
                put("/{id}") {
                    val dto = service.updateHookah(call.userId(), call.parameters["id"]!!, call.receive())
                    if (dto == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found")) else call.respond(dto)
                }
                delete("/{id}") {
                    if (service.deleteHookah(call.userId(), call.parameters["id"]!!)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
                }
            }
        }
    }
}
