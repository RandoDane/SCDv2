// scd.wtf market proxy for the SCD mod. The mod ships without any API key: players' price requests
// come here and are forwarded to https://market.scd.wtf/api with the one mod key, which only ever
// lives on this server (SCD_MARKET_KEY env var, or ~/.config/scd/mod_key). The key is never logged
// or sent back. Only the endpoints the mod uses are allowed, answers are cached, and every client
// IP gets its own rate limit so one player can't drain the shared quota.
import express from 'express';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';

const UPSTREAM = 'https://market.scd.wtf/api';
const KEY_FILE = path.join(os.homedir(), '.config', 'scd', 'mod_key');
const ALLOWED = /^\/(bazaar\/products|bazaar\/product\/[A-Za-z0-9_:;.%-]+(\/history)?|bazaar\/flips|auction\/(price|prices|search|value)|calendar|mayor)$/;
const PER_MINUTE = 120;
const MAX_CACHE = 3000;

let cachedKey = null;
let keyMtime = 0;
function marketKey() {
  if (process.env.SCD_MARKET_KEY) return process.env.SCD_MARKET_KEY.trim();
  try {
    const st = fs.statSync(KEY_FILE);
    if (st.mtimeMs !== keyMtime) {
      cachedKey = fs.readFileSync(KEY_FILE, 'utf8').trim();
      keyMtime = st.mtimeMs;
    }
    return cachedKey;
  } catch {
    return null;
  }
}

function ttlFor(p) {
  if (p === '/bazaar/products') return 30_000;
  if (p.endsWith('/history')) return 5 * 60_000;
  if (p === '/calendar' || p === '/mayor') return 5 * 60_000;
  return 60_000;
}

export function marketProxy() {
  const router = express.Router();
  const cache = new Map(); // key -> { at, ttl, status, body, type }
  const buckets = new Map(); // ip -> { tokens, at }

  function allow(ip) {
    const now = Date.now();
    const b = buckets.get(ip) ?? { tokens: PER_MINUTE, at: now };
    b.tokens = Math.min(PER_MINUTE, b.tokens + ((now - b.at) / 60_000) * PER_MINUTE);
    b.at = now;
    if (b.tokens < 1) {
      buckets.set(ip, b);
      return false;
    }
    b.tokens -= 1;
    buckets.set(ip, b);
    return true;
  }
  setInterval(() => {
    const cutoff = Date.now() - 10 * 60_000;
    for (const [ip, b] of buckets) if (b.at < cutoff) buckets.delete(ip);
    for (const [k, v] of cache) if (Date.now() - v.at > v.ttl) cache.delete(k);
  }, 60_000).unref();

  router.use(express.json({ limit: '64kb' }));

  router.all('*', async (req, res) => {
    const p = req.path;
    if (!ALLOWED.test(p) || !['GET', 'POST'].includes(req.method) || (req.method === 'POST' && p !== '/auction/value')) {
      return res.status(404).json({ detail: 'not a market endpoint' });
    }
    if (!allow(req.ip)) {
      res.set('retry-after', '30');
      return res.status(429).json({ detail: 'too many price requests - slow down' });
    }
    const key = marketKey();
    if (!key) return res.status(503).json({ detail: 'market proxy has no key configured' });

    const query = req.originalUrl.includes('?') ? req.originalUrl.slice(req.originalUrl.indexOf('?')) : '';
    const body = req.method === 'POST' ? JSON.stringify(req.body ?? {}) : null;
    const cacheKey = req.method + ' ' + p + query + (body ? ' ' + crypto.createHash('sha1').update(body).digest('hex') : '');
    const hit = cache.get(cacheKey);
    if (hit && Date.now() - hit.at < hit.ttl) {
      res.set('x-scd-cache', 'hit');
      return res.status(hit.status).type(hit.type).send(hit.body);
    }

    try {
      const upstream = await fetch(UPSTREAM + p + query, {
        method: req.method,
        headers: { 'X-API-Key': key, 'User-Agent': 'SCD-proxy', Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}) },
        body,
        redirect: 'manual',
        signal: AbortSignal.timeout(15_000),
      });
      const text = await upstream.text();
      const type = upstream.headers.get('content-type') || 'application/json';
      if (upstream.status === 429) res.set('retry-after', upstream.headers.get('retry-after') || '60');
      if (upstream.status === 200) {
        if (cache.size >= MAX_CACHE) cache.delete(cache.keys().next().value);
        cache.set(cacheKey, { at: Date.now(), ttl: ttlFor(p), status: 200, body: text, type });
      }
      // A rejected key is the server's problem, not the player's: don't leak upstream detail.
      if (upstream.status === 401 || upstream.status === 403) return res.status(502).json({ detail: 'market proxy key rejected' });
      res.status(upstream.status).type(type).send(text);
    } catch (e) {
      res.status(502).json({ detail: 'market unreachable' });
    }
  });
  return router;
}
