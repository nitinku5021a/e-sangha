# Meditation Hall API

Kotlin + Ktor modular monolith from `tech.md`. Default local store is H2; set `JDBC_URL` for PostgreSQL.

## Run locally

```bash
cd backend
gradlew.bat run
```

API: `http://localhost:8080/v1`

## Auth

Android package for Google OAuth: `com.digital.meditationsangha`

Set on the server (OCI):

```text
GOOGLE_WEB_CLIENT_ID=<Web client ID from Google Cloud>
JWT_SECRET=<at least 32 random characters>
AUTH_DEV_ENABLED=false
```

Google Sign-In:

```bash
curl -X POST http://localhost:8080/v1/auth/google \
  -H "Content-Type: application/json" \
  -d "{\"idToken\":\"GOOGLE_ID_TOKEN\"}"
```

Response: `accessToken`, `refreshToken`, `expiresInSeconds`, `userId`, `displayName`.

Use `Authorization: Bearer <accessToken>`. Refresh with `POST /v1/auth/refresh`. Logout with `POST /v1/auth/logout`.

`POST /v1/auth/dev` exists only when `AUTH_DEV_ENABLED=true` and must stay off in OCI.

Google Cloud console:

1. Create OAuth consent screen.
2. Android client: package `com.digital.meditationsangha` + SHA-1 of the signing key. Do not reuse the Vipassana Timer (`com.digital.sanghaworld`) OAuth client.
3. Web client: copy the client ID into `GOOGLE_WEB_CLIENT_ID` (this is the ID token audience).

## Docker

```bash
docker compose up postgres redis
# then run the API with JDBC_URL=jdbc:postgresql://localhost:5432/meditation
```

PostgreSQL and Redis are not published as public internet services in production; they stay on the private network of the VM.
