const STORAGE = "e-sangha-session";

function apiBase() {
  const configured = import.meta.env.VITE_API_BASE || "";
  return configured.replace(/\/$/, "");
}

export function loadSession() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE) || "null");
  } catch {
    return null;
  }
}

export function saveSession(tokens) {
  localStorage.setItem(STORAGE, JSON.stringify(tokens));
}

export function clearSession() {
  localStorage.removeItem(STORAGE);
}

function errorMessage(body, status) {
  const detail = body?.error;
  if (detail?.message) return detail.message;
  if (typeof body === "string" && body) return body;
  return `Request failed (${status})`;
}

async function raw(method, path, { body, auth = true, retry = true } = {}) {
  const session = loadSession();
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth && session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;
  const response = await fetch(`${apiBase()}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  if (response.status === 401 && auth && retry && session?.refreshToken) {
    const refreshed = await refresh(session.refreshToken);
    if (refreshed) return raw(method, path, { body, auth, retry: false });
  }
  if (response.status === 204) return null;
  const text = await response.text();
  let parsed = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
  }
  if (!response.ok) {
    const error = new Error(errorMessage(parsed, response.status));
    error.status = response.status;
    error.code = parsed?.error?.code;
    throw error;
  }
  return parsed;
}

async function refresh(refreshToken) {
  try {
    const tokens = await raw("POST", "/v1/auth/refresh", {
      body: { refreshToken },
      auth: false,
      retry: false
    });
    saveSession(tokens);
    return true;
  } catch {
    clearSession();
    return false;
  }
}

export const api = {
  loginName(displayName, email, password) {
    return raw("POST", "/v1/auth/name", {
      auth: false,
      body: {
        displayName,
        email,
        password,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"
      }
    });
  },
  loginGoogle(idToken) {
    return raw("POST", "/v1/auth/google", {
      auth: false,
      body: { idToken, device: "web" }
    });
  },
  logout() {
    const session = loadSession();
    const done = session?.refreshToken
      ? raw("POST", "/v1/auth/logout", {
          auth: false,
          body: { refreshToken: session.refreshToken }
        }).catch(() => null)
      : Promise.resolve();
    return done.finally(clearSession);
  },
  me: () => raw("GET", "/v1/me"),
  time: () => raw("GET", "/v1/time"),
  halls: () => raw("GET", "/v1/halls"),
  hall: (id) => raw("GET", `/v1/halls/${id}`),
  hallByCode: (code) => raw("GET", `/v1/halls/by-code/${encodeURIComponent(code)}`),
  createHall(body) {
    return raw("POST", "/v1/halls", { body });
  },
  joinHall: (id) => raw("POST", `/v1/halls/${id}/join`),
  ensureSession: (hallId) => raw("POST", `/v1/halls/${hallId}/sessions/ensure`),
  joinSession: (id) => raw("POST", `/v1/sessions/${id}/join`),
  session: (id) => raw("GET", `/v1/sessions/${id}`),
  participants: (id) => raw("GET", `/v1/sessions/${id}/participants`),
  leaveSession(id, attendedSeconds, clockFinished) {
    const q = `attendedSeconds=${attendedSeconds}&clockFinished=${clockFinished ? "true" : "false"}`;
    return raw("POST", `/v1/sessions/${id}/leave?${q}`);
  },
  logs: () => raw("GET", "/v1/me/meditation-log"),
  stats: () => raw("GET", "/v1/me/stats")
};

export function realtimeUrl() {
  const session = loadSession();
  if (!session?.accessToken) return null;
  const base = apiBase();
  let origin;
  if (base) {
    const url = new URL(base);
    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    origin = url.origin;
  } else {
    const proto = location.protocol === "https:" ? "wss:" : "ws:";
    origin = `${proto}//${location.host}`;
  }
  return `${origin}/v1/realtime?access_token=${encodeURIComponent(session.accessToken)}`;
}
