package com.hookahguide

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

/** JWT-токены и проверка паролей. */
object Security {
    const val ISSUER = "hookahguide"
    const val AUDIENCE = "hookahguide-app"
    const val REALM = "hookahguide"

    private const val TOKEN_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 дней

    lateinit var secret: String
        private set

    fun configure(jwtSecret: String) { secret = jwtSecret }

    private val algorithm: Algorithm get() = Algorithm.HMAC256(secret)

    fun makeToken(userId: String, email: String, nowMs: Long): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim("uid", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(nowMs + TOKEN_TTL_MS))
            .sign(algorithm)

    fun verifier() = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()

    fun hashPassword(raw: String): String =
        BCrypt.withDefaults().hashToString(12, raw.toCharArray())

    fun verifyPassword(raw: String, hash: String): Boolean =
        BCrypt.verifyer().verify(raw.toCharArray(), hash).verified
}
