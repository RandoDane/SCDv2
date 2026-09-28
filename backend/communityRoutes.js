// Community secret routes for the SCD mod: players publish routes they recorded
// (/scd route publish), everyone's mod downloads them as the "Community routes" pack.
//
//   GET  /api/routes/pack   -> route pack JSON (the same format as the mod's my_routes.json)
//   GET  /api/routes        -> {rooms: {room: count}, total}
//   POST /api/routes        {room, steps:[...], author} -> {stored, id}
//
// Only real room names, at most 60 steps and 64 KB per route, identical routes are stored once,
// at most 10 routes per room (the oldest go), and each IP may publish 60 routes an hour.
import express from 'express';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const PER_ROOM = 10;
const PER_HOUR = 60;

export function communityRoutes({ dataDir, roomNames }) {
  const router = express.Router();
  fs.mkdirSync(dataDir, { recursive: true });
  const names = new Set(JSON.parse(fs.readFileSync(roomNames, 'utf8')));
  const file = path.join(dataDir, 'routes.json');
  let routes = [];
  try { routes = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { routes = []; }
  const uploads = new Map();
  const save = () => fs.writeFileSync(file, JSON.stringify(routes));

  router.use(express.json({ limit: '64kb' }));

  router.get('/pack', (req, res) => {
    const pack = { '#name': 'Community routes', '#author': 'SCD players', Version: '1.0.0' };
    const count = {};
    for (const r of routes) {
      count[r.room] = (count[r.room] || 0) + 1;
      pack[count[r.room] === 1 ? r.room : `${r.room}:${count[r.room]}`] = r.steps;
    }
    res.json(pack);
  });

  router.get('/', (req, res) => {
    const rooms = {};
    for (const r of routes) rooms[r.room] = (rooms[r.room] || 0) + 1;
    res.json({ rooms, total: routes.length });
  });

  router.post('/', (req, res) => {
    const now = Date.now();
    const recent = (uploads.get(req.ip) || []).filter((t) => now - t < 3600_000);
    if (recent.length >= PER_HOUR) return res.status(429).json({ detail: 'too many routes published' });
    const { room, steps, author } = req.body || {};
    if (!names.has(room)) return res.status(400).json({ detail: 'unknown room' });
    if (!Array.isArray(steps) || steps.length === 0 || steps.length > 60 || !steps.every((s) => s && typeof s === 'object' && !Array.isArray(s))) {
      return res.status(400).json({ detail: 'bad route' });
    }
    const id = crypto.createHash('sha1').update(room + JSON.stringify(steps)).digest('hex').slice(0, 12);
    if (routes.some((r) => r.id === id)) return res.json({ stored: false, id });
    routes.push({ id, room, steps, author: typeof author === 'string' ? author.slice(0, 16) : '', at: now });
    const inRoom = routes.filter((r) => r.room === room);
    if (inRoom.length > PER_ROOM) {
      const oldest = inRoom.sort((a, b) => a.at - b.at)[0];
      routes = routes.filter((r) => r !== oldest);
    }
    save();
    recent.push(now);
    uploads.set(req.ip, recent);
    res.json({ stored: true, id });
  });
  return router;
}
