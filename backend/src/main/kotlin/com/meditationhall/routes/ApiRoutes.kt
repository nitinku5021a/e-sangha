package com.meditationhall.routes

import com.meditationhall.auth.AuthService
import com.meditationhall.dto.AuthRequest
import com.meditationhall.dto.CreateHallRequest
import com.meditationhall.dto.UpdateHallRequest
import com.meditationhall.dto.GoogleAuthRequest
import com.meditationhall.dto.LogoutRequest
import com.meditationhall.dto.RefreshRequest
import com.meditationhall.dto.SupportRequestBody
import com.meditationhall.dto.SupporterProfileBody
import com.meditationhall.dto.TimeResponse
import com.meditationhall.dto.WsEvent
import com.meditationhall.realtime.RealtimeHub
import com.meditationhall.services.HallService
import com.meditationhall.services.SessionService
import com.meditationhall.services.SupportService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.UUID
import kotlinx.serialization.json.Json

fun Route.apiRoutes(
    auth: AuthService,
    halls: HallService,
    sessions: SessionService,
    support: SupportService,
    hub: RealtimeHub
) {
    val json = Json { ignoreUnknownKeys = true }

    route("/v1") {
        post("/auth/google") {
            val body = call.receive<GoogleAuthRequest>()
            call.respond(auth.loginWithGoogle(body.idToken, body.device))
        }
        post("/auth/refresh") {
            val body = call.receive<RefreshRequest>()
            call.respond(auth.refresh(body.refreshToken))
        }
        post("/auth/logout") {
            val body = runCatching { call.receive<LogoutRequest>() }.getOrDefault(LogoutRequest())
            auth.logout(body.refreshToken)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/auth/dev") {
            val body = runCatching { call.receive<AuthRequest>() }.getOrDefault(AuthRequest())
            call.respond(auth.loginDev(body.displayName))
        }
        get("/me") {
            val user = auth.requireUser(call.request.header("Authorization"))
            call.respond(auth.me(user))
        }
        get("/time") {
            call.respond(TimeResponse(System.currentTimeMillis()))
        }

        get("/halls") {
            val user = auth.requireUser(call.request.header("Authorization"))
            call.respond(halls.list(user))
        }
        get("/halls/by-code/{code}") {
            val user = auth.requireUser(call.request.header("Authorization"))
            call.respond(halls.findByShareCode(user, call.parameters["code"].orEmpty()))
        }
        post("/halls") {
            val user = auth.requireUser(call.request.header("Authorization"))
            val body = call.receive<CreateHallRequest>()
            call.respond(HttpStatusCode.Created, halls.create(user, body))
        }
        get("/halls/{id}") {
            val user = auth.requireUser(call.request.header("Authorization"))
            val id = UUID.fromString(call.parameters["id"])
            call.respond(halls.get(user, id))
        }
        patch("/halls/{id}") {
            val user = auth.requireUser(call.request.header("Authorization"))
            val id = UUID.fromString(call.parameters["id"])
            call.respond(halls.update(user, id, call.receive<UpdateHallRequest>()))
        }
        post("/halls/{id}") {
            val user = auth.requireUser(call.request.header("Authorization"))
            val id = UUID.fromString(call.parameters["id"])
            call.respond(halls.update(user, id, call.receive<UpdateHallRequest>()))
        }
        delete("/halls/{id}") {
            val user = auth.requireUser(call.request.header("Authorization"))
            val id = UUID.fromString(call.parameters["id"])
            halls.delete(user, id)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/halls/{id}/join") {
            val user = auth.requireUser(call.request.header("Authorization"))
            val id = UUID.fromString(call.parameters["id"])
            halls.join(user, id)
            call.respond(halls.get(user, id))
        }
        delete("/halls/{id}/leave") {
            val user = auth.requireUser(call.request.header("Authorization"))
            val id = UUID.fromString(call.parameters["id"])
            halls.leave(user, id)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/halls/{id}/sessions/ensure") {
            val user = auth.requireUser(call.request.header("Authorization"))
            val hallId = UUID.fromString(call.parameters["id"])
            val sessionId = sessions.ensureSession(hallId)
            call.respond(sessions.get(sessionId))
        }
        get("/sessions/{id}") {
            auth.requireUser(call.request.header("Authorization"))
            call.respond(sessions.get(UUID.fromString(call.parameters["id"])))
        }
        get("/sessions/{id}/participants") {
            auth.requireUser(call.request.header("Authorization"))
            call.respond(sessions.participants(UUID.fromString(call.parameters["id"])))
        }
        post("/sessions/{id}/join") {
            val user = auth.requireUser(call.request.header("Authorization"))
            call.respond(sessions.join(user, UUID.fromString(call.parameters["id"])))
        }
        post("/sessions/{id}/leave") {
            val user = auth.requireUser(call.request.header("Authorization"))
            sessions.leave(user, UUID.fromString(call.parameters["id"]))
            call.respond(HttpStatusCode.NoContent)
        }

        get("/me/meditation-log") {
            val user = auth.requireUser(call.request.header("Authorization"))
            call.respond(sessions.logs(user))
        }
        get("/me/stats") {
            val user = auth.requireUser(call.request.header("Authorization"))
            call.respond(sessions.stats(user))
        }

        post("/support/requests") {
            val user = auth.requireUser(call.request.header("Authorization"))
            call.respond(support.request(user, call.receive<SupportRequestBody>()))
        }
        get("/support/requests") {
            val user = auth.requireUser(call.request.header("Authorization"))
            call.respond(support.listRequests(user))
        }
        post("/support/requests/{id}/cancel") {
            val user = auth.requireUser(call.request.header("Authorization"))
            support.cancel(user, UUID.fromString(call.parameters["id"]))
            call.respond(HttpStatusCode.NoContent)
        }
        post("/supporter/profile") {
            val user = auth.requireUser(call.request.header("Authorization"))
            val body = call.receive<SupporterProfileBody>()
            support.upsertProfile(user, body)
            call.respond(support.profile(user))
        }
        get("/supporter/profile") {
            val user = auth.requireUser(call.request.header("Authorization"))
            call.respond(support.profile(user))
        }
        get("/support/matches") {
            val user = auth.requireUser(call.request.header("Authorization"))
            call.respond(support.matchesFor(user))
        }
        post("/support/matches/{id}/accept") {
            val user = auth.requireUser(call.request.header("Authorization"))
            support.acceptMatch(user, UUID.fromString(call.parameters["id"]))
            call.respond(HttpStatusCode.NoContent)
        }
        post("/support/matches/{id}/decline") {
            val user = auth.requireUser(call.request.header("Authorization"))
            support.declineMatch(user, UUID.fromString(call.parameters["id"]))
            call.respond(HttpStatusCode.NoContent)
        }
        get("/support/relationships") {
            val user = auth.requireUser(call.request.header("Authorization"))
            call.respond(support.relationships(user))
        }
        post("/support/relationships/{id}/sessions") {
            val user = auth.requireUser(call.request.header("Authorization"))
            val hallId = support.createSupportSession(user, UUID.fromString(call.parameters["id"]), halls)
            call.respond(mapOf("hallId" to hallId))
        }

        webSocket("/realtime") {
            val token = call.request.queryParameters["token"]
            val user = auth.requireUser(if (token != null) "Bearer $token" else call.request.header("Authorization"))
            hub.register(user, this)
            send(Frame.Text(json.encodeToString(WsEvent.serializer(), WsEvent(type = "SERVER_TIME", serverTimeMillis = System.currentTimeMillis()))))
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        if (text.contains("heartbeat") || text.contains("HEARTBEAT")) {
                            send(Frame.Text(json.encodeToString(WsEvent.serializer(), WsEvent(type = "SERVER_TIME", serverTimeMillis = System.currentTimeMillis()))))
                        }
                    }
                }
            } finally {
                hub.unregister(this)
            }
        }
    }
}
