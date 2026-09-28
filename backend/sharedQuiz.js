// Quiz answers shared between SCD mods: each mod reports answers it saw confirmed correct
// (QuizMemory), everyone downloads the most-reported answer per question.
//
//   GET  /api/quiz  -> {question: answer}
//   POST /api/quiz  {question, answer} -> {ok}
//
// Questions up to 200 chars, answers up to 100; one vote per IP per question; 60 reports an hour per IP.
import express from 'express';
import fs from 'node:fs';
import path from 'node:path';

export function sharedQuiz({ dataDir }) {
  const router = express.Router();
  fs.mkdirSync(dataDir, { recursive: true });
  const file = path.join(dataDir, 'quiz.json');
  let votes = {}; // question -> {answer -> [ip hashes]}
  try { votes = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { votes = {}; }
  const reports = new Map();
  const save = () => fs.writeFileSync(file, JSON.stringify(votes));

  router.use(express.json({ limit: '4kb' }));

  router.get('/', (req, res) => {
    const out = {};
    for (const [q, answers] of Object.entries(votes)) {
      let best = null, n = 0;
      for (const [a, ips] of Object.entries(answers)) if (ips.length > n) { best = a; n = ips.length; }
      if (best) out[q] = best;
    }
    res.json(out);
  });

  router.post('/', (req, res) => {
    const now = Date.now();
    const recent = (reports.get(req.ip) || []).filter((t) => now - t < 3600_000);
    if (recent.length >= 60) return res.status(429).json({ detail: 'too many reports' });
    const { question, answer } = req.body || {};
    if (typeof question !== 'string' || typeof answer !== 'string' || !question.trim() || !answer.trim()
        || question.length > 200 || answer.length > 100) return res.status(400).json({ detail: 'bad answer' });
    const answers = (votes[question] ??= {});
    // One vote per address per question: a changed answer moves the vote.
    for (const ips of Object.values(answers)) {
      const i = ips.indexOf(req.ip);
      if (i >= 0) ips.splice(i, 1);
    }
    (answers[answer] ??= []).push(req.ip);
    save();
    recent.push(now);
    reports.set(req.ip, recent);
    res.json({ ok: true });
  });
  return router;
}
