// SCD live debug sessions - drop-in Express router for the SCD backend.
//
// The mod (/scd record start) POSTs batches of recording events here every ~2s; a watcher
// (tools/watch_session.py) polls them back while you play. Each session is claimed by the
// random token sent with its first batch; every later write and every read must present it.
//
// Mount it in server/src/server.js (it does not open any port of its own):
//     import { debugSessions } from './debugSessions.js';
//     app.use('/api/debug', debugSessions({ dataDir: new URL('../data/debug-sessions', import.meta.url).pathname }));
//
// Endpoints:
//   POST /api/debug/sessions/:id/events   body {token, events:[...]}           -> {ok, stored, lastSeq}
//   GET  /api/debug/sessions/:id/events?after=<seq>&limit=<n>  (X-SCD-Debug-Token header)
//                                                                              -> {events, lastSeq, player, startedAt}
//   GET  /api/debug/sessions/:id           (token header)                     -> summary
//
// Sessions live in memory for 48h and are also appended to <dataDir>/<id>.jsonl.

import express from 'express';
import fs from 'node:fs';
import path from 'node:path';

const ID_RE = /^[0-9a-f]{8}$/;
const TOKEN_RE = /^[0-9a-f]{16}$/;
const MAX_BATCH = 1000;
const MAX_EVENTS = 500000;
const TTL_MS = 48 * 60 * 60 * 1000;

export function debugSessions({ dataDir } = {}) {
  const router = express.Router();
  const sessions = new Map();

  if (dataDir) fs.mkdirSync(dataDir, { recursive: true });

  setInterval(() => {
    const now = Date.now();
    for (const [id, s] of sessions) if (now - s.lastAt > TTL_MS) sessions.delete(id);
  }, 60 * 60 * 1000).unref();

  function tokenOf(req) {
    return req.get('X-SCD-Debug-Token') || req.query.token || (req.body && req.body.token);
  }

  function authorized(req, res) {
    const id = req.params.id;
    if (!ID_RE.test(id)) {
      res.status(400).json({ error: 'bad session id' });
      return null;
    }
    const session = sessions.get(id);
    if (!session) {
      res.status(404).json({ error: 'unknown session' });
      return null;
    }
    if (tokenOf(req) !== session.token) {
      res.status(403).json({ error: 'wrong token' });
      return null;
    }
    return session;
  }

  router.post('/sessions/:id/events', express.json({ limit: '4mb' }), (req, res) => {
    const id = req.params.id;
    const token = tokenOf(req);
    const events = req.body && req.body.events;
    if (!ID_RE.test(id) || !TOKEN_RE.test(token || '')) return res.status(400).json({ error: 'bad session id or token' });
    if (!Array.isArray(events) || events.length > MAX_BATCH) return res.status(400).json({ error: 'events must be an array of <= ' + MAX_BATCH });

    let session = sessions.get(id);
    if (!session) {
      session = { token, events: [], startedAt: Date.now(), lastAt: Date.now(), player: null, lastSeq: 0 };
      sessions.set(id, session);
    } else if (session.token !== token) {
      return res.status(403).json({ error: 'wrong token' });
    }

    const lines = [];
    for (const e of events) {
      if (typeof e !== 'object' || e === null || typeof e.seq !== 'number') continue;
      if (e.seq <= session.lastSeq) continue; // retried batch - already stored
      e.received = Date.now();
      if (e.type === 'start' && e.player) session.player = e.player;
      session.events.push(e);
      session.lastSeq = e.seq;
      lines.push(JSON.stringify(e));
    }
    if (session.events.length > MAX_EVENTS) session.events.splice(0, session.events.length - MAX_EVENTS);
    session.lastAt = Date.now();
    if (dataDir && lines.length) fs.appendFile(path.join(dataDir, id + '.jsonl'), lines.join('\n') + '\n', () => {});
    res.json({ ok: true, stored: lines.length, lastSeq: session.lastSeq });
  });

  router.get('/sessions/:id/events', (req, res) => {
    const session = authorized(req, res);
    if (!session) return;
    const after = Number(req.query.after || 0);
    const limit = Math.min(Number(req.query.limit || 2000), 5000);
    const out = [];
    for (const e of session.events) {
      if (e.seq > after) {
        out.push(e);
        if (out.length >= limit) break;
      }
    }
    res.json({ events: out, lastSeq: session.lastSeq, player: session.player, startedAt: session.startedAt });
  });

  router.get('/sessions/:id', (req, res) => {
    const session = authorized(req, res);
    if (!session) return;
    res.json({ player: session.player, startedAt: session.startedAt, lastAt: session.lastAt, events: session.events.length, lastSeq: session.lastSeq });
  });

  return router;
}
