package com.meditationhall

import com.meditationhall.auth.AuthService
import com.meditationhall.db.DatabaseFactory
import com.meditationhall.realtime.RealtimeHub
import com.meditationhall.routes.apiRoutes
import com.meditationhall.scheduler.SessionScheduler
import com.meditationhall.services.HallService
import com.meditationhall.services.SessionService
import com.meditationhall.services.SupportService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    val hub = RealtimeHub()
    val auth = AuthService()
    val halls = HallService()
    val sessions = SessionService(hub)
    val support = SupportService()
    SessionScheduler(sessions, halls).start()

    install(CallId) {
        retrieve { it.request.header(HttpHeaders.XRequestId) }
        generate { UUID.randomUUID().toString() }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("requestId")
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("Idempotency-Key")
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(WebSockets)
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                cause.status,
                ErrorBody(ErrorDetail(cause.code, cause.message ?: "Error", call.callIdOrUnknown()))
            )
        }
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorBody(ErrorDetail("INTERNAL", "Unexpected error", call.callIdOrUnknown()))
            )
        }
    }
    routing {
        apiRoutes(auth, halls, sessions, support, hub)
    }
}

fun io.ktor.server.application.ApplicationCall.callIdOrUnknown(): String =
    request.header(HttpHeaders.XRequestId) ?: "unknown"

class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String
) : RuntimeException(message)

@Serializable
data class ErrorBody(val error: ErrorDetail)

@Serializable
data class ErrorDetail(val code: String, val message: String, val requestId: String)
