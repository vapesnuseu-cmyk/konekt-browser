/* KONEKT Browser sync — the signed-in user's stored browser state:
   bookmarks, history, speed-dials, settings and appearance, as one
   versioned blob. GET to pull, PUT to push. Bearer-token auth. */
'use strict';
const { redis, parse, userFromReq, cors, ok, fail, readBody } = require('../lib/kb.js');

const MAX = 900 * 1024; // guard the blob size

/* merge incoming into existing, two levels deep. Each device only sends the
   keys it manages, so a phone push never wipes the desktop's HUD (and vice
   versa). Arrays and scalars are replaced; plain objects merge one level. */
const isObj = v => v && typeof v === 'object' && !Array.isArray(v);
function merge2(base, add) {
  const out = Object.assign({}, base);
  for (const k of Object.keys(add || {})) {
    out[k] = (isObj(out[k]) && isObj(add[k])) ? Object.assign({}, out[k], add[k]) : add[k];
  }
  return out;
}

module.exports = async function handler(req, res) {
  cors(res);
  if (req.method === 'OPTIONS') return res.status(204).end();

  let me;
  try { me = await userFromReq(req); } catch { return fail(res, 500, 'Server error'); }
  if (!me) return fail(res, 401, 'Not signed in');
  const key = 'kb:data:' + me.handle;

  try {
    if (req.method === 'GET') {
      const rec = parse(await redis(['GET', key])) || { data: {}, ver: 0 };
      return ok(res, { data: rec.data || {}, ver: rec.ver || 0, updated: rec.updated || 0 });
    }

    if (req.method === 'PUT' || req.method === 'POST') {
      const b = await readBody(req);
      const data = b && typeof b.data === 'object' && b.data ? b.data : {};
      const s = JSON.stringify(data);
      if (s.length > MAX) return fail(res, 413, 'Too much data to sync');
      const prev = parse(await redis(['GET', key])) || { ver: 0, data: {} };
      const merged = b.replace ? data : merge2(prev.data || {}, data);
      const rec = { data: merged, ver: (prev.ver || 0) + 1, updated: Date.now() };
      await redis(['SET', key, JSON.stringify(rec)]);
      return ok(res, { ver: rec.ver, updated: rec.updated });
    }

    return fail(res, 405, 'Use GET or PUT');
  } catch (e) {
    return fail(res, 500, /not-configured/.test(String(e && e.message)) ? 'Storage is not configured' : 'Server error');
  }
};
