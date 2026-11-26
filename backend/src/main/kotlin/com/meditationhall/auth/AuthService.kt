package com.meditationhall.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.meditationhall.ApiException
import com.meditationhall.db.Attendance
import com.meditationhall.db.CommunityPresence
import com.meditationhall.db.HallMemberships
import com.meditationhall.db.HallSchedules
import com.meditationhall.db.Halls
import com.meditationhall.db.MeditationLogs
import com.meditationhall.db.MeditationSessions
import com.meditationhall.db.Notifications
import com.meditationhall.db.RefreshTokens
import com.meditationhall.db.SupportRelationships
import com.meditationhall.db.SupportRequests
import com.meditationhall.db.SupporterProfiles
import com.meditationhall.db.UserDevices
import com.meditationhall.db.Users
import com.meditationhall.db.now
import com.meditationhall.db.uuid
import com.meditationhall.dto.MeResponse
import com.meditationhall.dto.TokenResponse
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import java.util.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
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
                ?: identity.email?.trim()?.lowercase()?.let { address ->
                    Users.selectAll().firstOrNull { it[Users.email]?.trim()?.lowercase() == address }
                }
            val userId = if (existing != null) {
                val status = existing[Users.status]
                if (status != "ACTIVE") {
                    throw ApiException(HttpStatusCode.Forbidden, "ACCOUNT_${status}", "This account cannot sign in.")
                }
                Users.update({ Users.id eq existing[Users.id] }) {
                    if (!identity.email.isNullOrBlank()) it[email] = identity.email.trim().lowercase()
                    if (existing[Users.googleSub] == null) it[googleSub] = identity.subject
                    it[updatedAt] = now()
                }
                existing[Users.id]
            } else {
                val id = uuid()
                Users.insert {
                    it[Users.id] = id
                    it[displayName] = identity.name?.take(120)?.ifBlank { null } ?: "Practitioner"
                    it[email] = identity.email?.trim()?.lowercase()
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

    fun loginWithName(displayName: String, email: String, password: String): TokenResponse {
        val name = displayName.trim().take(120)
        val address = email.trim().lowercase().take(255)
        if (name.length < 2) {
            throw ApiException(HttpStatusCode.BadRequest, "NAME_REQUIRED", "Enter the name you want others to see.")
        }
        if (!address.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            throw ApiException(HttpStatusCode.BadRequest, "EMAIL_REQUIRED", "Enter a valid email address.")
        }
        if (password.length < 8) {
            throw ApiException(HttpStatusCode.BadRequest, "PASSWORD_REQUIRED", "Use a password of at least 8 characters.")
        }
        return transaction {
            val existing = Users.selectAll().firstOrNull {
                it[Users.email]?.trim()?.lowercase() == address
            }
            val userId = if (existing != null) {
                val status = existing[Users.status]
                if (status != "ACTIVE") {
                    throw ApiException(HttpStatusCode.Forbidden, "ACCOUNT_${status}", "This account cannot sign in.")
                }
                val stored = existing[Users.passwordHash]
                if (stored.isNullOrBlank()) {
                    throw ApiException(
                        HttpStatusCode.Forbidden,
                        "USE_GOOGLE",
                        "This email is signed in with Google."
                    )
                }
                if (!passwordMatches(password, stored)) {
                    throw ApiException(HttpStatusCode.Unauthorized, "BAD_PASSWORD", "That password does not match this email.")
                }
                Users.update({ Users.id eq existing[Users.id] }) {
                    it[displayName] = name
                    it[updatedAt] = now()
                }
                existing[Users.id]
            } else {
                val id = uuid()
                Users.insert {
                    it[Users.id] = id
                    it[Users.displayName] = name
                    it[Users.email] = address
                    it[passwordHash] = hashPassword(password)
                    it[timezone] = "UTC"
                    it[status] = "ACTIVE"
                    it[createdAt] = now()
                    it[updatedAt] = now()
                }
                id
            }
            issueTokens(userId, "android-name")
        }
    }

    private fun hashPassword(password: String): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val hash = pbkdf2(password, salt)
        val enc = Base64.getEncoder()
        return "pbkdf2$120000$${enc.encodeToString(salt)}$${enc.encodeToString(hash)}"
    }

    private fun passwordMatches(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = pbkdf2(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int = 120000): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
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

    fun deleteAccount(userId: UUID) {
        transaction {
            val hallIds = Halls.selectAll().where { Halls.creatorId eq userId }.map { it[Halls.id] }
            if (hallIds.isNotEmpty()) {
                val sessionIds = MeditationSessions.selectAll()
                    .where { MeditationSessions.hallId inList hallIds }
                    .map { it[MeditationSessions.id] }
                if (sessionIds.isNotEmpty()) {
                    Attendance.deleteWhere { Attendance.sessionId inList sessionIds }
                }
                MeditationLogs.deleteWhere { MeditationLogs.hallId inList hallIds }
                Attendance.deleteWhere { Attendance.hallId inList hallIds }
                HallMemberships.deleteWhere { HallMemberships.hallId inList hallIds }
                HallSchedules.deleteWhere { HallSchedules.hallId inList hallIds }
                MeditationSessions.deleteWhere { MeditationSessions.hallId inList hallIds }
                Halls.deleteWhere { Halls.id inList hallIds }
            }
            MeditationLogs.deleteWhere { MeditationLogs.userId eq userId }
            Attendance.deleteWhere { Attendance.userId eq userId }
            HallMemberships.deleteWhere { HallMemberships.userId eq userId }
            SupportRequests.deleteWhere { SupportRequests.requesterId eq userId }
            SupporterProfiles.deleteWhere { SupporterProfiles.userId eq userId }
            SupportRelationships.deleteWhere {
                (SupportRelationships.supporterId eq userId) or (SupportRelationships.supportedUserId eq userId)
            }
            CommunityPresence.deleteWhere { CommunityPresence.userId eq userId }
            Notifications.deleteWhere { Notifications.userId eq userId }
            UserDevices.deleteWhere { UserDevices.userId eq userId }
            RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
            Users.deleteWhere { Users.id eq userId }
        }
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

    fun updateDisplayName(userId: UUID, displayName: String, timezone: String = ""): MeResponse = transaction {
        val name = displayName.trim().take(120)
        val zone = timezone.trim().take(64)
        if (name.isBlank() && zone.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_PROFILE", "Nothing to update.")
        }
        if (zone.isNotBlank()) {
            runCatching { java.time.ZoneId.of(zone) }.getOrElse {
                throw ApiException(HttpStatusCode.BadRequest, "INVALID_TIMEZONE", "Unknown time zone.")
            }
        }
        Users.update({ Users.id eq userId }) {
            if (name.isNotBlank()) it[Users.displayName] = name
            if (zone.isNotBlank()) it[Users.timezone] = zone
            it[updatedAt] = now()
        }
        if (name.isNotBlank()) {
            CommunityPresence.update({ CommunityPresence.userId eq userId }) {
                it[CommunityPresence.displayName] = name
            }
        }
        me(userId)
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
