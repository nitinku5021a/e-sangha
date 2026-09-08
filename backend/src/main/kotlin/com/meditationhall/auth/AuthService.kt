package com.meditationhall.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.meditationhall.ApiException
import com.meditationhall.db.RefreshTokens
import com.meditationhall.db.Users
import com.meditationhall.db.now
import com.meditationhall.db.uuid
import com.meditationhall.dto.MeResponse
import com.meditationhall.dto.TokenResponse
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import java.util.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class AuthService {
    private val jwtSecret = System.getenv("JWT_SECRET")?.takeIf { it.length >= 32 }
        ?: "dev-only-change-me-before-oci-deploy!!!!"
    private val algorithm = Algorithm.HMAC256(jwtSecret)
    private val issuer = "sanghaworld"
    private val accessTtlMinutes = 15L
    private val refreshTtlDays = 30L
    private val google = GoogleIdTokens(System.getenv("GOOGLE_WEB_CLIENT_ID").orEmpty())
    private val random = SecureRandom()
    private val devEnabled = System.getenv("AUTH_DEV_ENABLED") == "true"

    fun loginWithGoogle(idToken: String, device: String?): TokenResponse {
        val identity = google.verify(idToken.trim())
        return transaction {
            val existing = Users.selectAll().firstOrNull { it[Users.googleSub] == identity.subject }
            val userId = if (existing != null) {
                val status = existing[Users.status]
                if (status != "ACTIVE") {
                    throw ApiException(HttpStatusCode.Forbidden, "ACCOUNT_${status}", "This account cannot sign in.")
                }
                Users.update({ Users.id eq existing[Users.id] }) {
                    if (!identity.name.isNullOrBlank()) it[displayName] = identity.name.take(120)
                    if (!identity.email.isNullOrBlank()) it[email] = identity.email
                    it[updatedAt] = now()
                }
                existing[Users.id]
            } else {
                val id = uuid()
                Users.insert {
                    it[Users.id] = id
                    it[displayName] = identity.name?.take(120)?.ifBlank { null } ?: "Practitioner"
                    it[email] = identity.email
                    it[googleSub] = identity.subject
                    it[timezone] = "UTC"
                    it[status] = "ACTIVE"
                    it[createdAt] = now()
                    it[updatedAt] = now()
                }
                id
            }
            issueTokens(userId, device)
        }
    }

    fun loginDev(displayName: String): TokenResponse {
        if (!devEnabled) {
            throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "Not found.")
        }
        return transaction {
            val name = displayName.trim().ifBlank { "You" }
            val existing = Users.selectAll().firstOrNull {
                it[Users.displayName] == name && it[Users.status] == "ACTIVE" && it[Users.googleSub] == null
            }
            val userId = if (existing != null) existing[Users.id] else {
                val id = uuid()
                Users.insert {
                    it[Users.id] = id
                    it[Users.displayName] = name
                    it[timezone] = "UTC"
                    it[status] = "ACTIVE"
                    it[createdAt] = now()
                    it[updatedAt] = now()
                }
                id
            }
            issueTokens(userId, "dev")
        }
    }

    fun refresh(refreshToken: String): TokenResponse = transaction {
        val hash = hashToken(refreshToken)
        val row = RefreshTokens.selectAll().firstOrNull { it[RefreshTokens.tokenHash] == hash }
            ?: throw ApiException(HttpStatusCode.Unauthorized, "INVALID_REFRESH", "Refresh token is invalid.")
        if (row[RefreshTokens.revokedAt] != null) {
            throw ApiException(HttpStatusCode.Unauthorized, "INVALID_REFRESH", "Refresh token was revoked.")
        }
        if (row[RefreshTokens.expiresAt].isBefore(Instant.now())) {
            throw ApiException(HttpStatusCode.Unauthorized, "INVALID_REFRESH", "Refresh token expired.")
        }
        RefreshTokens.update({ RefreshTokens.id eq row[RefreshTokens.id] }) {
            it[revokedAt] = now()
        }
        val userId = row[RefreshTokens.userId]
        assertActive(userId)
        issueTokens(userId, row[RefreshTokens.device])
    }

    fun logout(refreshToken: String?) {
        if (refreshToken.isNullOrBlank()) return
        transaction {
            val hash = hashToken(refreshToken)
            RefreshTokens.update({ RefreshTokens.tokenHash eq hash }) {
                it[revokedAt] = now()
            }
        }
    }

    fun requireUser(authorization: String?): UUID {
        val token = authorization?.removePrefix("Bearer ")?.trim().orEmpty()
        if (token.isBlank()) {
            throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Missing bearer token.")
        }
        val userId = try {
            val decoded = JWT.require(algorithm).withIssuer(issuer).build().verify(token)
            UUID.fromString(decoded.subject)
        } catch (_: JWTVerificationException) {
            throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Invalid or expired access token.")
        } catch (_: IllegalArgumentException) {
            throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Invalid access token.")
        }
        return transaction { assertActive(userId) }
    }

    fun me(userId: UUID): MeResponse = transaction {
        val row = Users.selectAll().firstOrNull { it[Users.id] == userId }
            ?: throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "User not found.")
        MeResponse(
            userId = row[Users.id].toString(),
            displayName = row[Users.displayName],
            email = row[Users.email],
            status = row[Users.status]
        )
    }

    private fun assertActive(userId: UUID): UUID {
        val row = Users.selectAll().firstOrNull { it[Users.id] == userId }
            ?: throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "User not found.")
        if (row[Users.status] != "ACTIVE") {
            throw ApiException(HttpStatusCode.Forbidden, "ACCOUNT_${row[Users.status]}", "This account cannot sign in.")
        }
        return userId
    }

    private fun issueTokens(userId: UUID, device: String?): TokenResponse {
        val accessExpires = Instant.now().plus(accessTtlMinutes, ChronoUnit.MINUTES)
        val access = JWT.create()
            .withIssuer(issuer)
            .withSubject(userId.toString())
            .withExpiresAt(Date.from(accessExpires))
            .withIssuedAt(Date())
            .sign(algorithm)
        val refreshRaw = newRefreshToken()
        val refreshExpires = Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS)
        RefreshTokens.insert {
            it[id] = uuid()
            it[RefreshTokens.userId] = userId
            it[tokenHash] = hashToken(refreshRaw)
            it[expiresAt] = refreshExpires
            it[createdAt] = now()
            it[RefreshTokens.device] = device?.take(80)
        }
        val user = Users.selectAll().first { it[Users.id] == userId }
        return TokenResponse(
            accessToken = access,
            refreshToken = refreshRaw,
            expiresInSeconds = accessTtlMinutes * 60,
            userId = userId.toString(),
            displayName = user[Users.displayName]
        )
    }

    private fun newRefreshToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
