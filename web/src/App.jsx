import React, { useEffect, useMemo, useRef, useState } from "react";
import { api, clearSession, loadSession, realtimeUrl, saveSession } from "./api.js";

const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID || "";

function formatClock(totalSeconds) {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  const mm = String(m).padStart(2, "0");
  const ss = String(s).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

function scheduleLine(hall) {
  const time = hall.startLocalTime || "";
  const kind = hall.scheduleType || "";
  const days = hall.daysOfWeek ? ` ${hall.daysOfWeek}` : "";
  return [time, kind, days].filter(Boolean).join(" · ");
}

export default function App() {
  const [session, setSession] = useState(loadSession);
  const [view, setView] = useState(session ? "halls" : "login");
  const [error, setError] = useState("");
  const [halls, setHalls] = useState(null);
  const [selected, setSelected] = useState(null);
  const [logs, setLogs] = useState(null);
  const [stats, setStats] = useState(null);
  const [sitting, setSitting] = useState(null);

  function signedIn(tokens) {
    saveSession(tokens);
    setSession(tokens);
    setView("halls");
    setError("");
  }

  async function signOut() {
    await api.logout();
    setSession(null);
    setHalls(null);
    setSelected(null);
    setSitting(null);
    setView("login");
  }

  if (!session || view === "login") {
    return <Login onSignedIn={signedIn} error={error} setError={setError} />;
  }

  return (
    <div className="app">
      <header className="bar">
        <h1>Meditation Sangha</h1>
        <nav>
          <button className="quiet" onClick={() => { setSitting(null); setView("halls"); }}>Halls</button>
          <button className="quiet" onClick={() => { setSitting(null); setView("log"); }}>Log</button>
          <button className="quiet" onClick={signOut}>Sign out</button>
        </nav>
      </header>
      <p className="muted">{session.displayName}</p>
      {error && <div className="error">{error}</div>}
      {sitting && (
        <Sitting
          sitting={sitting}
          onClose={() => { setSitting(null); setView("halls"); }}
          onError={setError}
        />
      )}
      {!sitting && view === "halls" && (
        <Halls
          halls={halls}
          setHalls={setHalls}
          selected={selected}
          setSelected={setSelected}
          onSit={setSitting}
          onError={setError}
        />
      )}
      {!sitting && view === "log" && (
        <Log logs={logs} setLogs={setLogs} stats={stats} setStats={setStats} onError={setError} />
      )}
    </div>
  );
}

function Login({ onSignedIn, error, setError }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const googleRef = useRef(null);

  useEffect(() => {
    if (!googleClientId || !googleRef.current) return;
    let cancelled = false;
    const start = () => {
      if (cancelled || !window.google?.accounts?.id) return false;
      window.google.accounts.id.initialize({
        client_id: googleClientId,
        callback: async (response) => {
          try {
            setBusy(true);
            const tokens = await api.loginGoogle(response.credential);
            onSignedIn(tokens);
          } catch (e) {
            setError(e.message);
          } finally {
            setBusy(false);
          }
        }
      });
      window.google.accounts.id.renderButton(googleRef.current, {
        theme: "outline",
        size: "large",
        width: 280
      });
      return true;
    };
    if (start()) return () => { cancelled = true; };
    const timer = setInterval(() => { if (start()) clearInterval(timer); }, 300);
    return () => { cancelled = true; clearInterval(timer); };
  }, [onSignedIn, setError]);

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const tokens = await api.loginName(name.trim(), email.trim(), password);
      onSignedIn(tokens);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="app">
      <header className="bar"><h1>Meditation Sangha</h1></header>
      <div className="card stack">
        <h2>Sit with others</h2>
        <p className="muted">The same halls as the Android app. Sign in with email, or with Google when this site has a client id.</p>
        <div ref={googleRef} />
        {!googleClientId && <p className="muted">Set VITE_GOOGLE_CLIENT_ID to enable Google sign-in.</p>}
        <form className="stack" onSubmit={submit}>
          <label><span>Name</span><input value={name} onChange={(e) => setName(e.target.value)} required minLength={2} /></label>
          <label><span>Email</span><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
          <label><span>Password</span><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} /></label>
          {error && <div className="error">{error}</div>}
          <button disabled={busy} type="submit">{busy ? "Signing in…" : "Continue with email"}</button>
        </form>
      </div>
    </div>
  );
}

function HallCard({ hall, onOpen }) {
  return (
    <button className="card" style={{ textAlign: "left", color: "#344334", background: "#fff", borderRadius: 16 }} onClick={() => onOpen(hall)}>
      <strong>{hall.name}</strong>
      <div className="muted">
        {hall.participantCount} sitting · {scheduleLine(hall) || `${Math.round(hall.durationSeconds / 60)} min`}
      </div>
    </button>
  );
}

function Halls({ halls, setHalls, selected, setSelected, onSit, onError }) {
  const [code, setCode] = useState("");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    name: "",
    description: "",
    durationMinutes: 60,
    hour: 6,
    minute: 0
  });

  async function load() {
    try {
      setHalls(await api.halls());
    } catch (e) {
      if (e.status === 401) clearSession();
      onError(e.message);
    }
  }

  useEffect(() => { load(); }, []);

  async function openHall(hall) {
    onError("");
    try {
      setSelected(await api.hall(hall.id));
    } catch (e) {
      onError(e.message);
    }
  }

  async function openCode(event) {
    event.preventDefault();
    onError("");
    try {
      setSelected(await api.hallByCode(code.trim()));
    } catch (e) {
      onError(e.message);
    }
  }

  async function create(event) {
    event.preventDefault();
    onError("");
    try {
      const created = await api.createHall({
        ...form,
        durationMinutes: Number(form.durationMinutes),
        hour: Number(form.hour),
        minute: Number(form.minute),
        scheduleType: "DAILY",
        visibility: "PUBLIC",
        audioType: "BELL",
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"
      });
      setCreating(false);
      setSelected(created);
      load();
    } catch (e) {
      onError(e.message);
    }
  }

  async function sit(hall) {
    onError("");
    try {
      await api.joinHall(hall.id);
      const ensured = await api.ensureSession(hall.id);
      const joined = await api.joinSession(ensured.id);
      onSit({ hall, session: joined, joinedAt: Date.now() });
    } catch (e) {
      onError(e.message);
    }
  }

  const sections = useMemo(() => {
    if (!halls) return [];
    return [
      ["Sitting now", halls.sittingNow],
      ["Starting soon", halls.startingSoon],
      ["Your halls", halls.myHalls],
      ["All public halls", halls.catalog]
    ];
  }, [halls]);

  if (selected) {
    return (
      <div className="card stack">
        <button className="quiet" onClick={() => setSelected(null)}>Back</button>
        <h2>{selected.name}</h2>
        {selected.description && <p>{selected.description}</p>}
        <p className="muted">
          {scheduleLine(selected) || "Open sitting"} · {Math.round(selected.durationSeconds / 60)} min
          {selected.shareCode ? ` · code ${selected.shareCode}` : ""}
        </p>
        <p className="muted">{selected.participantCount} people in this sitting · {selected.memberCount} members</p>
        <button onClick={() => sit(selected)}>Join and sit</button>
      </div>
    );
  }

  return (
    <>
      <form className="row" onSubmit={openCode}>
        <input placeholder="Hall code" value={code} onChange={(e) => setCode(e.target.value)} />
        <button type="submit">Open</button>
        <button type="button" className="quiet" onClick={() => setCreating((v) => !v)}>New hall</button>
      </form>
      {creating && (
        <form className="card stack" onSubmit={create}>
          <label><span>Name</span><input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
          <label><span>Description</span><textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
          <div className="row">
            <label><span>Minutes</span><input type="number" min="1" value={form.durationMinutes} onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })} /></label>
            <label><span>Hour</span><input type="number" min="0" max="23" value={form.hour} onChange={(e) => setForm({ ...form, hour: e.target.value })} /></label>
            <label><span>Minute</span><input type="number" min="0" max="59" value={form.minute} onChange={(e) => setForm({ ...form, minute: e.target.value })} /></label>
          </div>
          <button type="submit">Create daily hall</button>
        </form>
      )}
      {!halls && <p className="muted">Loading halls…</p>}
      {sections.map(([title, list]) => (
        list?.length ? (
          <section key={title}>
            <h2 style={{ fontWeight: "normal" }}>{title}</h2>
            {list.map((hall) => <HallCard key={`${title}-${hall.id}`} hall={hall} onOpen={openHall} />)}
          </section>
        ) : null
      ))}
    </>
  );
}

function Sitting({ sitting, onClose, onError }) {
  const [session, setSession] = useState(sitting.session);
  const [people, setPeople] = useState([]);
  const [now, setNow] = useState(Date.now());
  const [offset, setOffset] = useState(0);
  const audioRef = useRef(null);
  const started = useRef(Date.now());

  useEffect(() => {
    const tick = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(tick);
  }, []);

  useEffect(() => {
    let socket;
    let closed = false;
    function connect() {
      const url = realtimeUrl();
      if (!url) return;
      socket = new WebSocket(url);
      socket.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === "SERVER_TIME" && msg.serverTimeMillis) {
            setOffset(msg.serverTimeMillis - Date.now());
          }
          if (msg.sessionId === session.id && msg.participantCount != null) {
            setSession((prev) => ({ ...prev, participantCount: msg.participantCount, status: msg.status || prev.status }));
          }
        } catch {
          /* ignore non-json frames */
        }
      };
      socket.onclose = () => {
        if (!closed) setTimeout(connect, 4000);
      };
    }
    connect();
    const beat = setInterval(() => {
      if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "HEARTBEAT" }));
    }, 20000);
    return () => {
      closed = true;
      clearInterval(beat);
      socket?.close();
    };
  }, [session.id]);

  useEffect(() => {
    let stop = false;
    async function pull() {
      try {
        const [next, seated] = await Promise.all([
          api.session(session.id),
          api.participants(session.id)
        ]);
        if (!stop) {
          setSession(next);
          setPeople(seated || []);
        }
      } catch (e) {
        if (!stop) onError(e.message);
      }
    }
    pull();
    const timer = setInterval(pull, 10000);
    return () => { stop = true; clearInterval(timer); };
  }, [session.id, onError]);

  useEffect(() => {
    const url = sitting.hall.audioUrl;
    const audio = audioRef.current;
    if (!url || !audio) return;
    audio.src = url;
    audio.play().catch(() => {});
  }, [sitting.hall.audioUrl]);

  const serverNow = now + offset;
  const endAt = session.actualStartMillis
    ? session.actualStartMillis + session.durationSeconds * 1000
    : session.scheduledStartMillis + session.durationSeconds * 1000;
  const remaining = session.status === "SCHEDULED"
    ? Math.max(0, (session.scheduledStartMillis - serverNow) / 1000)
    : Math.max(0, (endAt - serverNow) / 1000);

  async function leave(finished) {
    const attended = Math.round((Date.now() - started.current) / 1000);
    try {
      await api.leaveSession(session.id, attended, finished || remaining <= 1);
    } catch (e) {
      onError(e.message);
    }
    onClose();
  }

  return (
    <div className="card">
      <p className="muted">{sitting.hall.name}</p>
      <h2>{session.status === "SCHEDULED" ? "Begins in" : "Sitting"}</h2>
      <p className="clock">{formatClock(remaining)}</p>
      <p className="muted">{people.length || session.participantCount} here</p>
      <div className="seats">
        {people.map((person) => (
          <div className="seat" key={person.userId}>{person.displayName || "Practitioner"}</div>
        ))}
      </div>
      {sitting.hall.audioUrl && <audio ref={audioRef} />}
      <div className="row" style={{ marginTop: "1rem" }}>
        <button onClick={() => leave(false)}>Leave quietly</button>
      </div>
    </div>
  );
}

function Log({ logs, setLogs, stats, setStats, onError }) {
  useEffect(() => {
    Promise.all([api.logs(), api.stats()])
      .then(([entries, summary]) => {
        setLogs(entries || []);
        setStats(summary);
      })
      .catch((e) => onError(e.message));
  }, [setLogs, setStats, onError]);

  return (
    <>
      {stats && (
        <div className="card">
          <h2>Your record</h2>
          <p className="muted">
            {stats.sessionsCompleted} completed · {stats.sessionsAttended} attended · {Math.round((stats.totalMeditationSeconds || 0) / 60)} minutes
          </p>
        </div>
      )}
      {!logs && <p className="muted">Loading log…</p>}
      {logs?.map((entry) => (
        <div className="card" key={entry.id}>
          <strong>{entry.hallName}</strong>
          <div className="muted">{entry.date} · {Math.round(entry.durationSeconds / 60)} min · {entry.completionStatus}</div>
        </div>
      ))}
      {logs?.length === 0 && <p className="muted">No sittings recorded yet.</p>}
    </>
  );
}
