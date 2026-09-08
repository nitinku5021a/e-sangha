package com.meditationhall.auth

import com.meditationhall.ApiException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import io.ktor.http.HttpStatusCode
import java.net.URI

data class GoogleIdentity(
    val subject: String,
    val email: String?,
    val name: String?
)

class GoogleIdTokens(
    private val webClientId: String
) {
    private val processor = DefaultJWTProcessor<SecurityContext>().apply {
        val jwkSource = RemoteJWKSet<SecurityContext>(URI("https://www.googleapis.com/oauth2/v3/certs").toURL())
        jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
    }

    fun verify(idToken: String): GoogleIdentity {
        if (webClientId.isBlank()) {
            throw ApiException(
                HttpStatusCode.ServiceUnavailable,
                "AUTH_NOT_CONFIGURED",
                "GOOGLE_WEB_CLIENT_ID is not set on the server."
            )
        }
        val claims = runCatching { processor.process(idToken, null) }.getOrElse {
            throw ApiException(HttpStatusCode.Unauthorized, "INVALID_GOOGLE_TOKEN", "Google ID token could not be verified.")
        }
        val issuer = claims.issuer
        if (issuer != "https://accounts.google.com" && issuer != "accounts.google.com") {
            throw ApiException(HttpStatusCode.Unauthorized, "INVALID_GOOGLE_TOKEN", "Unexpected token issuer.")
        }
        if (!claims.audience.contains(webClientId)) {
            throw ApiException(HttpStatusCode.Unauthorized, "INVALID_GOOGLE_TOKEN", "Token audience does not match this app.")
        }
        val sub = claims.subject?.takeIf { it.isNotBlank() }
            ?: throw ApiException(HttpStatusCode.Unauthorized, "INVALID_GOOGLE_TOKEN", "Token is missing subject.")
        return GoogleIdentity(
            subject = sub,
            email = claims.getStringClaim("email"),
            name = claims.getStringClaim("name")
        )
    }
}
