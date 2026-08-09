/* KONEKT Browser accounts — register | login | logout | check | me.
   Token-based (Bearer), so it works from the desktop app's file:// origin
   without cookies. One dispatcher to stay inside Vercel's function cap. */
'use strict';
const {
  redis, HANDLE_RE, RESERVED, hashPassword, verifyPassword,
  getUser, publicUser, createSession, userFromReq,
  cors, ok, fail, readBody, clean
} = require('../lib/kb.js');

module.exports = async function handler(req, res) {
  cors(res);
  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'POST') return fail(res, 405, 'Use POST');

  let b;
  try { b = await readBody(req); } catch { return fail(res, 400, 'Bad request'); }
  const action = clean(b.action, 12);

  try {
    if (action === 'register') {
      const handle = clean(b.handle, 20).toLowerCase();
      const password = String(b.password == null ? '' : b.password);
      const name = clean(b.name, 40) || handle;
      const email = clean(b.email, 120).toLowerCase();
      if (!HANDLE_RE.test(handle)) return fail(res, 400, 'Handle must be 3-20 characters: a-z, 0-9, underscore');
      if (password.length < 8) return fail(res, 400, 'Password must be at least 8 characters');
      if (RESERVED.has(handle)) return fail(res, 409, '@' + handle + ' is reserved');
      if (await getUser(handle)) return fail(res, 409, '@' + handle + ' is taken');
      if (email && await redis(['GET', 'kb:email:' + email])) return fail(res, 409, 'That email already has an account');
      const { salt, hash } = hashPassword(password);
      const user = { handle, name, email, salt, hash, created: Date.now() };
      await redis(['SET', 'kb:u:' + handle, JSON.stringify(user)]);
      if (email) await redis(['SET', 'kb:email:' + email, handle]);
      const token = await createSession(handle);
      return ok(res, { token, user: publicUser(user) });
    }

    if (action === 'login') {
      const handle = clean(b.handle, 20).toLowerCase().replace(/^@/, '');
      const password = String(b.password == null ? '' : b.password);
      if (!handle || !password) return fail(res, 400, 'Handle and password are required');
      const user = await getUser(handle);
      if (!user || !verifyPassword(password, user.salt, user.hash))
        return fail(res, 401, 'Wrong handle or password');
      const token = await createSession(handle);
      return ok(res, { token, user: publicUser(user) });
    }

    if (action === 'check') {
      const handle = clean(b.handle, 20).toLowerCase();
      if (!HANDLE_RE.test(handle)) return ok(res, { valid: false, taken: false });
      if (RESERVED.has(handle)) return ok(res, { valid: true, taken: true });
      return ok(res, { valid: true, taken: Boolean(await getUser(handle)) });
    }

    if (action === 'me') {
      const me = await userFromReq(req);
      if (!me) return fail(res, 401, 'Not signed in');
      return ok(res, { user: publicUser(me) });
    }

    if (action === 'logout') {
      const me = await userFromReq(req);
      if (me && me._token) await redis(['DEL', 'kb:sess:' + me._token]);
      return ok(res, { ok: true });
    }

    return fail(res, 400, 'Unknown action');
  } catch (e) {
    return fail(res, 500, /not-configured/.test(String(e && e.message)) ? 'Storage is not configured' : 'Server error');
  }
};
