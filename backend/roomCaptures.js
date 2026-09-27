// Shared collection of captured dungeon rooms for the SCD mod.
//
// Every player's mod copies the rooms it plays through (RoomCapture) and uploads the ones this
// server doesn't have yet, so everyone's /scd rooms shows the shared progress and /scd rooms build
// can lay out rooms anyone captured. Puzzle rooms are kept once per block-layout variation.
//
//   GET  /api/rooms/captures            -> {rooms:[{id,name,signature,puzzle,at}], total}
//   GET  /api/rooms/captures/:id        -> the room file (gzip NBT)
//   POST /api/rooms/captures            raw gzip NBT body; headers X-SCD-Room (URI-encoded name),
//                                       X-SCD-Signature, X-SCD-Puzzle ("1"/"0") -> {stored, id}
//
// Only real room names are accepted, bodies must be gzip and at most 4 MB, nothing is ever
// overwritten, and each IP may upload 40 rooms an hour.
import express from 'express';
import fs from 'node:fs';
import path from 'node:path';

const MAX_BYTES = 4 * 1024 * 1024;
const PER_HOUR = 40;

export function roomCaptures({ dataDir, roomNames }) {
  const router = express.Router();
  fs.mkdirSync(dataDir, { recursive: true });
  const names = new Set(JSON.parse(fs.readFileSync(roomNames, 'utf8')));
  const indexFile = path.join(dataDir, 'index.json');
  let index = [];
  try { index = JSON.parse(fs.readFileSync(indexFile, 'utf8')); } catch { index = []; }
  const uploads = new Map(); // ip -> [timestamps]

  const safe = (s) => s.replace(/[^A-Za-z0-9 _'-]/g, '_');
  const save = () => fs.writeFileSync(indexFile, JSON.stringify(index, null, 1));

  router.get('/', (req, res) => {
    res.json({ rooms: index.map(({ id, name, signature, puzzle, at }) => ({ id, name, signature, puzzle, at })), total: names.size });
  });

  router.get('/:id', (req, res) => {
    const entry = index.find((e) => e.id === req.params.id);
    if (!entry) return res.status(404).json({ detail: 'no such room' });
    res.type('application/octet-stream').sendFile(path.join(dataDir, entry.id + '.nbt'));
  });

  router.post('/', express.raw({ type: '*/*', limit: MAX_BYTES }), (req, res) => {
    const now = Date.now();
    const recent = (uploads.get(req.ip) || []).filter((t) => now - t < 3600_000);
    if (recent.length >= PER_HOUR) return res.status(429).json({ detail: 'too many uploads' });
    let name;
    try { name = decodeURIComponent(req.get('X-SCD-Room') || ''); } catch { name = ''; }
    const signature = Number.parseInt(req.get('X-SCD-Signature') || '', 10);
    const puzzle = req.get('X-SCD-Puzzle') === '1';
    const body = req.body;
    if (!names.has(name)) return res.status(400).json({ detail: 'unknown room' });
    if (!Number.isFinite(signature)) return res.status(400).json({ detail: 'bad signature' });
    if (!Buffer.isBuffer(body) || body.length < 20 || body[0] !== 0x1f || body[1] !== 0x8b) return res.status(400).json({ detail: 'not a room file' });
    // Nothing is overwritten: a room once (a puzzle once per variation).
    if (index.some((e) => e.name === name && (!puzzle || e.signature === signature))) return res.json({ stored: false });
    let id = safe(name);
    for (let n = 2; index.some((e) => e.id === id); n++) id = safe(name) + ` (${n})`;
    fs.writeFileSync(path.join(dataDir, id + '.nbt'), body);
    index.push({ id, name, signature, puzzle, at: now, size: body.length });
    save();
    recent.push(now);
    uploads.set(req.ip, recent);
    res.json({ stored: true, id });
  });
  return router;
}
