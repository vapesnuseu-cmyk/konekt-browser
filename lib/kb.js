/* ================================================================
   KONEKT Browser — server helpers (accounts + sync).
   Zero dependencies: talks to Upstash over its REST API with the
   built-in fetch, hashes with node:crypto. Every key is namespaced
   `kb:` so the browser's accounts never touch KONEKT's own data
   even when they share one Redis.
   ================================================================ */
'use strict';
const crypto = require('crypto');

const URL   = process.env.KV_REST_API_URL   || process.env.UPSTASH_REDIS_REST_URL;
const TOKEN = process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN;
const hasRedis = Boolean(URL && TOKEN);

/* one Redis command over the REST API; throws on Redis-level errors */
async function redis(cmd) {
  if (!hasRedis) throw new Error('storage-not-configured');
  const r = await fetch(URL, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + TOKEN, 'Content-Type': 'application/json' },
    body: JSON.stringify(cmd)
  });
  const j = await r.json();
  if (j.error) throw new Error(j.error);
  return j.result;
}
const parse = v => { if (v == null) return null; if (typeof v === 'object') return v; try { return JSON.parse(v); } catch { return null; } };

/* ---------------- accounts ---------------- */
const HANDLE_RE = /^[a-z0-9_]{3,20}$/;
const RESERVED = new Set(['admin','root','support','system','konekt','api','www','me','null','undefined']);

function hashPassword(pw) {
  const salt = crypto.randomBytes(16).toString('hex');
  const hash = crypto.pbkdf2Sync(pw, salt, 120000, 64, 'sha512').toString('hex');
  return { salt, hash };
}
function verifyPassword(pw, salt, hash) {
  if (!salt || !hash) return false;
  const test = crypto.pbkdf2Sync(pw, salt, 120000, 64, 'sha512').toString('hex');
  const a = Buffer.from(test), b = Buffer.from(hash);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

const getUser = async handle => parse(await redis(['GET', 'kb:u:' + handle]));
const publicUser = u => u && ({ handle: u.handle, name: u.name || u.handle, email: u.email || '', created: u.created || 0 });

async function createSession(handle) {
  const token = crypto.randomBytes(32).toString('hex');
  await redis(['SET', 'kb:sess:' + token, handle, 'EX', String(30 * 86400)]);
  return token;
}
async function userFromReq(req) {
  const h = req.headers['authorization'] || req.headers['Authorization'] || '';
  const m = /^Bearer\s+([a-f0-9]{16,})$/i.exec(String(h));
  if (!m) return null;
  const handle = await redis(['GET', 'kb:sess:' + m[1]]);
  if (!handle) return null;
  const u = await getUser(String(handle));
  if (u) u._token = m[1];
  return u;
}

/* ---------------- http plumbing ---------------- */
function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'content-type, authorization');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, OPTIONS');
  res.setHeader('Access-Control-Max-Age', '86400');
}
const ok   = (res, data) => { res.status(200).json(Object.assign({ ok: true }, data)); };
const fail = (res, code, error) => { res.status(code).json({ ok: false, error }); };

async function readBody(req) {
  if (req.body && typeof req.body === 'object') return req.body;
  if (typeof req.body === 'string') { try { return JSON.parse(req.body); } catch { return {}; } }
  const chunks = [];
  for await (const c of req) chunks.push(c);
  if (!chunks.length) return {};
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { return {}; }
}
const clean = (v, max) => String(v == null ? '' : v).slice(0, max).trim();

module.exports = {
  redis, parse, hasRedis,
  HANDLE_RE, RESERVED,
  hashPassword, verifyPassword, getUser, publicUser,
  createSession, userFromReq,
  cors, ok, fail, readBody, clean
};
