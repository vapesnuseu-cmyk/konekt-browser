/* ================================================================
   KONEKT BROWSER — main process
   NKO Intl. Foundation of Technological Research & Development

   One BrowserWindow hosts the chrome (browser.html); every tab is a
   <webview> guest living in the persist:konekt partition. The main
   process owns everything a guest page must not: downloads,
   permission prompts, popup policy, native context menus, and the
   keyboard chords that have to win even when a page has focus.
   ================================================================ */
'use strict';
const { app, BrowserWindow, ipcMain, session, shell, Menu, clipboard, net } = require('electron');
const path = require('path');
const fs = require('fs');

const PARTITION = 'persist:konekt';
const SMOKE = process.argv.includes('--smoke');
const smokeDir = (() => {
  const i = process.argv.indexOf('--smoke-dir');
  return i > -1 && process.argv[i + 1] ? process.argv[i + 1] : app.getPath('temp');
})();

let win = null;

/* ---------- single instance: a second launch focuses the first and
   hands over any URL it was started with ---------- */
if (!app.requestSingleInstanceLock()) app.quit();
app.on('second-instance', (_e, argv) => {
  if (!win) return;
  if (win.isMinimized()) win.restore();
  win.focus();
  const url = argv.find(a => /^https?:\/\//i.test(a));
  if (url) win.webContents.send('open-url', url, false);
});

/* ---------- look like Chrome to the web ----------
   Sites gate features (and whole login flows) on the UA. Dropping the
   Electron and app tokens leaves the underlying Chrome UA, which is
   what the engine actually is. */
const CLEAN_UA = app.userAgentFallback
  .replace(/\sElectron\/[\d.]+/i, '')
  .replace(new RegExp('\\s' + app.getName().replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '/[\\d.]+', 'i'), '');
app.userAgentFallback = CLEAN_UA;

/* ---------- remembered permission decisions ---------- */
const permFile = () => path.join(app.getPath('userData'), 'permissions.json');
let permStore = {};
try { permStore = JSON.parse(fs.readFileSync(permFile(), 'utf8')); } catch { permStore = {}; }
function savePerms() {
  try { fs.writeFileSync(permFile(), JSON.stringify(permStore)); } catch {}
}
function originOf(u) { try { return new URL(u).origin; } catch { return u || ''; } }
const AUTO_ALLOW = new Set(['fullscreen', 'pointerLock', 'clipboard-sanitized-write', 'keyboardLock', 'window-management']);
const pendingPerms = new Map();
let permSeq = 0;

/* ---------- downloads ---------- */
const dlItems = new Map();
let dlSeq = 0;
function uniquePath(dir, name) {
  const ext = path.extname(name), base = path.basename(name, ext);
  let p = path.join(dir, name), n = 1;
  while (fs.existsSync(p)) p = path.join(dir, base + ' (' + n++ + ')' + ext);
  return p;
}

/* ---------- built-in ad & tracker blocker ----------
   Domain-level, ad/tracker hosts only — no login, CDN or social-SDK
   domains, so sites keep working with it on. Toggled from Settings. */
const ADBLOCK_HOSTS = [
  'doubleclick.net', 'googlesyndication.com', 'googleadservices.com', 'adservice.google.com',
  '2mdn.net', 'adnxs.com', 'criteo.com', 'criteo.net', 'taboola.com', 'outbrain.com',
  'scorecardresearch.com', 'quantserve.com', 'quantcount.com', 'moatads.com', 'adsrvr.org',
  'amazon-adsystem.com', 'pubmatic.com', 'rubiconproject.com', 'openx.net', 'yieldmo.com',
  'smartadserver.com', 'adform.net', 'bidswitch.net', 'casalemedia.com', '33across.com',
  'gumgum.com', 'sharethrough.com', 'teads.tv', 'zemanta.com', 'mathtag.com',
  'bluekai.com', 'demdex.net', 'krxd.net', 'exelator.com', 'agkn.com',
  'eyeota.net', 'tapad.com', 'rlcdn.com', 'adroll.com', 'serving-sys.com'
];
let adblockOn = true, adBlockedCount = 0, adCountTimer = null;
function applyAdblock(sess) {
  if (adblockOn) {
    const urls = [];
    for (const d of ADBLOCK_HOSTS) urls.push('*://' + d + '/*', '*://*.' + d + '/*');
    sess.webRequest.onBeforeRequest({ urls }, (_details, cb) => {
      adBlockedCount++;
      if (!adCountTimer) adCountTimer = setTimeout(() => {
        adCountTimer = null;
        send('adblock-count', adBlockedCount);
      }, 400);
      cb({ cancel: true });
    });
  } else {
    sess.webRequest.onBeforeRequest(null);
  }
}
ipcMain.on('adblock-set', (_e, arg) => {
  adblockOn = !!(arg && arg.on);
  applyAdblock(session.fromPartition(PARTITION));
});

/* ---------- keyboard chords that the chrome must receive even when a
   guest page has focus ---------- */
const CHORDS = new Set([
  'ctrl+t', 'ctrl+w', 'ctrl+shift+t', 'ctrl+tab', 'ctrl+shift+tab',
  'ctrl+l', 'ctrl+f', 'ctrl+h', 'ctrl+j', 'ctrl+d', 'ctrl+p',
  'ctrl+r', 'ctrl+shift+r', 'f5', 'f6', 'f11', 'f12',
  'ctrl+0', 'ctrl+-', 'ctrl+=', 'ctrl+plus',
  'ctrl+1', 'ctrl+2', 'ctrl+3', 'ctrl+4', 'ctrl+5', 'ctrl+6', 'ctrl+7', 'ctrl+8', 'ctrl+9',
  'alt+left', 'alt+right'
]);
function chordOf(input) {
  const k = (input.key || '').toLowerCase();
  let c = '';
  if (input.control) c += 'ctrl+';
  if (input.alt) c += 'alt+';
  if (input.shift) c += 'shift+';
  c += k === '+' ? 'plus' : k === 'arrowleft' ? 'left' : k === 'arrowright' ? 'right' : k;
  return c;
}

function send(ch, ...args) { if (win && !win.isDestroyed()) win.webContents.send(ch, ...args); }

/* ---------- native context menu for guest pages ---------- */
function guestMenu(contents, params) {
  const items = [];
  const push = (label, fn, enabled = true) => items.push({ label, enabled, click: fn });

  if (params.linkURL) {
    push('Open link in new tab', () => send('open-url', params.linkURL, false));
    push('Open link in background tab', () => send('open-url', params.linkURL, true));
    push('Copy link address', () => clipboard.writeText(params.linkURL));
    items.push({ type: 'separator' });
  }
  if (params.mediaType === 'image' && params.srcURL) {
    push('Open image in new tab', () => send('open-url', params.srcURL, false));
    push('Copy image', () => contents.copyImageAt(params.x, params.y));
    push('Save image', () => contents.downloadURL(params.srcURL));
    items.push({ type: 'separator' });
  }
  if ((params.mediaType === 'video' || params.mediaType === 'audio') && params.srcURL) {
    push('Copy media address', () => clipboard.writeText(params.srcURL));
    push('Save media', () => contents.downloadURL(params.srcURL));
    items.push({ type: 'separator' });
  }
  if (params.isEditable) {
    for (const s of (params.dictionarySuggestions || []).slice(0, 4))
      push(s, () => contents.replaceMisspelling(s));
    if (params.misspelledWord) items.push({ type: 'separator' });
    items.push({ role: 'cut', enabled: params.editFlags.canCut });
    items.push({ role: 'copy', enabled: params.editFlags.canCopy });
    items.push({ role: 'paste', enabled: params.editFlags.canPaste });
    items.push({ role: 'selectAll' });
    items.push({ type: 'separator' });
  } else if (params.selectionText && params.selectionText.trim()) {
    const sel = params.selectionText.trim();
    const short = sel.length > 40 ? sel.slice(0, 40) + '…' : sel;
    push('Copy', () => contents.copy());
    push('Search for “' + short + '”', () => send('open-url', 'konekt-search:' + sel, false));
    items.push({ type: 'separator' });
  }
  push('Back', () => contents.navigationHistory.goBack(), contents.navigationHistory.canGoBack());
  push('Forward', () => contents.navigationHistory.goForward(), contents.navigationHistory.canGoForward());
  push('Reload', () => contents.reload());
  items.push({ type: 'separator' });
  push('Print…', () => contents.print());
  push('Inspect element', () => { contents.inspectElement(params.x, params.y); });
  Menu.buildFromTemplate(items).popup({ window: win });
}

/* ---------- wire up every guest webContents ---------- */
app.on('web-contents-created', (_e, contents) => {
  if (contents.getType() !== 'webview') return;

  /* window.open / target=_blank / middle-click become tabs */
  contents.setWindowOpenHandler(({ url, disposition }) => {
    if (/^(https?|file|data|about):/i.test(url))
      send('open-url', url, disposition === 'background-tab');
    return { action: 'deny' };
  });

  contents.on('before-input-event', (event, input) => {
    if (input.type !== 'keyDown') return;
    const c = chordOf(input);
    if (c === 'escape') { send('chord', 'escape'); return; } // page keeps it too
    if (CHORDS.has(c)) { event.preventDefault(); send('chord', c); }
  });

  contents.on('zoom-changed', (_ev, dir) => send('zoom-step', dir === 'in' ? 1 : -1));
  contents.on('context-menu', (_ev, params) => guestMenu(contents, params));
  contents.setVisualZoomLevelLimits(1, 4).catch(() => {});
});

/* ---------- window ---------- */
function createWindow() {
  win = new BrowserWindow({
    width: 1280, height: 850, minWidth: 720, minHeight: 460,
    backgroundColor: '#000000',
    icon: path.join(__dirname, 'icon.png'),
    title: 'KONEKT Browser',
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#000000', symbolColor: '#ffffff', height: 38 },
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      webviewTag: true,
      spellcheck: true
    }
  });
  win.setMenuBarVisibility(false);

  /* guests must never gain node — enforce regardless of markup */
  win.webContents.on('will-attach-webview', (_e, webPreferences) => {
    delete webPreferences.preload;
    webPreferences.nodeIntegration = false;
    webPreferences.contextIsolation = true;
    webPreferences.sandbox = true;
  });

  win.on('enter-full-screen', () => send('fullscreen', true));
  win.on('leave-full-screen', () => send('fullscreen', false));
  /* mouse side buttons on Windows */
  win.on('app-command', (_e, cmd) => {
    if (cmd === 'browser-backward') send('chord', 'alt+left');
    if (cmd === 'browser-forward') send('chord', 'alt+right');
  });

  win.loadFile('browser.html');
}

/* ---------- IPC ---------- */
ipcMain.on('perm-response', (_e, { id, allow, remember, origin, permission }) => {
  const cb = pendingPerms.get(id);
  if (cb) { pendingPerms.delete(id); cb(!!allow); }
  if (remember && origin) { permStore[origin + '|' + permission] = allow ? 'allow' : 'block'; savePerms(); }
});
ipcMain.on('dl-action', (_e, { id, action }) => {
  const item = dlItems.get(id);
  if (!item) return;
  try {
    if (action === 'pause') item.pause();
    else if (action === 'resume') item.resume();
    else if (action === 'cancel') item.cancel();
  } catch {}
});
ipcMain.on('clip-write', (_e, s) => { if (typeof s === 'string') clipboard.writeText(s); });
ipcMain.on('shell-show', (_e, p) => { if (typeof p === 'string') shell.showItemInFolder(p); });
ipcMain.on('shell-open', (_e, p) => { if (typeof p === 'string') shell.openPath(p); });
ipcMain.on('win-fullscreen', () => { if (win) win.setFullScreen(!win.isFullScreen()); });
ipcMain.on('set-theme', (_e, { dark }) => {
  if (!win) return;
  try {
    win.setTitleBarOverlay(dark
      ? { color: '#000000', symbolColor: '#ffffff', height: 38 }
      : { color: '#ffffff', symbolColor: '#000000', height: 38 });
  } catch {}
});
/* Liquid Glass — Windows 11 acrylic behind the (translucent) chrome.
   No-op / harmless on older Windows and other platforms. */
ipcMain.on('set-material', (_e, { glass }) => {
  if (!win) return;
  try {
    win.setBackgroundColor(glass ? '#00000000' : '#000000');
    if (typeof win.setBackgroundMaterial === 'function') win.setBackgroundMaterial(glass ? 'acrylic' : 'none');
  } catch {}
});
/* ---------- self-update (portable build) ----------
   Download the release zip, extract it, and hand off to a tiny .cmd
   that waits for this process to exit, copies the new files over the
   install folder, and relaunches. Dev runs just open the page. */
ipcMain.on('self-update', (_e, { url }) => {
  if (!app.isPackaged) { shell.openExternal('https://github.com/vapesnuseu-cmyk/konekt-browser/releases/latest'); return; }
  try {
    const tmp = path.join(app.getPath('temp'), 'konekt-update-' + process.pid);
    fs.rmSync(tmp, { recursive: true, force: true }); fs.mkdirSync(tmp, { recursive: true });
    const zipPath = path.join(tmp, 'update.zip');
    const file = fs.createWriteStream(zipPath);
    const req = net.request(url);
    req.on('response', res => {
      const total = parseInt(res.headers['content-length'] || 0, 10); let got = 0;
      res.on('data', c => { got += c.length; file.write(c); if (total) send('update-progress', { phase: 'download', pct: Math.round(got / total * 100) }); });
      res.on('end', () => file.end(() => extractAndSwap(tmp, zipPath)));
    });
    req.on('error', e => send('update-progress', { phase: 'error', error: String(e && e.message || e) }));
    req.end();
  } catch (e) { send('update-progress', { phase: 'error', error: String(e && e.message || e) }); }
});
function extractAndSwap(tmp, zipPath) {
  send('update-progress', { phase: 'extract' });
  const { spawn } = require('child_process');
  const ps = spawn('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command',
    'Expand-Archive -LiteralPath ' + JSON.stringify(zipPath) + ' -DestinationPath ' + JSON.stringify(tmp) + ' -Force'], { windowsHide: true });
  ps.on('exit', code => {
    if (code !== 0) { send('update-progress', { phase: 'error', error: 'Extraction failed' }); return; }
    const installDir = path.dirname(process.execPath);
    const newDir = path.join(tmp, 'KONEKT-Browser-win-x64');
    if (!fs.existsSync(newDir)) { send('update-progress', { phase: 'error', error: 'Unexpected archive layout' }); return; }
    const cmdPath = path.join(tmp, 'apply-update.cmd');
    const pid = process.pid;
    const cmd = '@echo off\r\n'
      + ':wait\r\n'
      + 'tasklist /fi "PID eq ' + pid + '" | find "' + pid + '" >nul && (timeout /t 1 /nobreak >nul & goto wait)\r\n'
      + 'robocopy ' + q(newDir) + ' ' + q(installDir) + ' /E /IS /IT /R:2 /W:1 /NFL /NDL /NJH /NJS >nul\r\n'
      + 'start "" ' + q(path.join(installDir, path.basename(process.execPath))) + '\r\n'
      + 'timeout /t 2 /nobreak >nul\r\n'
      + 'rmdir /s /q ' + q(tmp) + '\r\n';
    fs.writeFileSync(cmdPath, cmd);
    send('update-progress', { phase: 'swap' });
    spawn('cmd.exe', ['/c', 'start', '""', '/min', cmdPath], { detached: true, stdio: 'ignore', windowsHide: true }).unref();
    setTimeout(() => app.quit(), 600);
  });
}
function q(s) { return '"' + s + '"'; }
ipcMain.on('clear-data', async () => {
  const s = session.fromPartition(PARTITION);
  await s.clearStorageData();
  await s.clearCache();
  send('dl', { state: 'data-cleared' });
});
ipcMain.handle('app-info', () => ({
  version: app.getVersion(),
  electron: process.versions.electron,
  chrome: process.versions.chrome,
  node: process.versions.node,
  downloads: app.getPath('downloads'),
  ua: CLEAN_UA,
  isPackaged: app.isPackaged
}));
let chromeReady = false;
ipcMain.on('chrome-ready', () => { chromeReady = true; });

/* ---------- boot ---------- */
app.whenReady().then(() => {
  const sess = session.fromPartition(PARTITION);
  sess.setUserAgent(CLEAN_UA);
  applyAdblock(sess);

  sess.setPermissionRequestHandler((wc, permission, callback, details) => {
    if (AUTO_ALLOW.has(permission)) return callback(true);
    const origin = originOf(details.requestingUrl || wc.getURL());
    const saved = permStore[origin + '|' + permission];
    if (saved === 'allow') return callback(true);
    if (saved === 'block') return callback(false);
    const id = ++permSeq;
    pendingPerms.set(id, callback);
    send('perm-request', { id, origin, permission });
  });
  sess.setPermissionCheckHandler((_wc, permission, origin) => {
    if (AUTO_ALLOW.has(permission)) return true;
    return permStore[originOf(origin) + '|' + permission] === 'allow';
  });

  sess.on('will-download', (_e, item) => {
    const id = ++dlSeq;
    const target = uniquePath(app.getPath('downloads'), item.getFilename() || 'download');
    item.setSavePath(target);
    dlItems.set(id, item);
    send('dl', {
      id, state: 'started', filename: path.basename(target), path: target,
      total: item.getTotalBytes(), url: item.getURL(), ts: Date.now()
    });
    item.on('updated', (_ev, state) => send('dl', {
      id, state: state === 'interrupted' ? 'interrupted' : 'progress',
      received: item.getReceivedBytes(), total: item.getTotalBytes(), paused: item.isPaused()
    }));
    item.once('done', (_ev, state) => {
      dlItems.delete(id);
      send('dl', { id, state, path: target, received: item.getReceivedBytes(), total: item.getTotalBytes() });
    });
  });

  createWindow();
  if (SMOKE) runSmoke();
});

app.on('window-all-closed', () => app.quit());
app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });

/* ================================================================
   --smoke: boot, screenshot the start page, load a real site,
   screenshot again (window + guest), print JSON, exit. Lets the
   build be verified without a human clicking around.
   ================================================================ */
const delay = ms => new Promise(r => setTimeout(r, ms));
async function runSmoke() {
  const out = {};
  try {
    await new Promise(res => win.webContents.once('did-finish-load', res));
    for (let i = 0; i < 50 && !chromeReady; i++) await delay(100);
    await delay(1200);
    fs.writeFileSync(path.join(smokeDir, 'smoke-start.png'),
      (await win.webContents.capturePage()).toPNG());
    out.startPage = true;
    out.sidebarAt = await win.webContents.executeJavaScript(
      '(function(){var el=document.elementFromPoint(23,132);return el?(el.id||el.tagName):"null";})()');

    await win.webContents.executeJavaScript('KB.smokeNav("https://example.com")');
    let st = {};
    for (let i = 0; i < 60; i++) {
      await delay(250);
      st = await win.webContents.executeJavaScript('KB.smokeState()');
      if (st && !st.loading && /example/.test(st.url || '')) break;
    }
    out.nav = st;
    await delay(700);
    fs.writeFileSync(path.join(smokeDir, 'smoke-site.png'),
      (await win.webContents.capturePage()).toPNG());

    /* second tab + history panel */
    await win.webContents.executeJavaScript('KB.newTab("https://www.wikipedia.org"), undefined');
    for (let i = 0; i < 60; i++) {
      await delay(250);
      const s2 = await win.webContents.executeJavaScript('KB.smokeState()');
      if (s2 && !s2.loading && /wikipedia/.test(s2.url || '')) { out.nav2 = s2; break; }
    }
    await win.webContents.executeJavaScript('togglePanel("pHist"), undefined');
    await delay(500);
    fs.writeFileSync(path.join(smokeDir, 'smoke-panels.png'),
      (await win.webContents.capturePage()).toPNG());
    await win.webContents.executeJavaScript('togglePanel("pHist"), undefined');

    /* Opera-style chrome: KONEKT side panel + speed dial + shield */
    await win.webContents.executeJavaScript('kpanelToggle(true), undefined');
    await win.webContents.executeJavaScript('KB.smokeNav("konekt://start"), undefined');
    await delay(3500);
    fs.writeFileSync(path.join(smokeDir, 'smoke-opera.png'),
      (await win.webContents.capturePage()).toPNG());
    out.adblock = await win.webContents.executeJavaScript('KB.adblockState()');

    /* Liquid Glass appearance on the Speed Dial, with the KONEKT panel open */
    await win.webContents.executeJavaScript('KB.smokeGlass(), undefined');
    await delay(1500);
    fs.writeFileSync(path.join(smokeDir, 'smoke-glass.png'),
      (await win.webContents.capturePage()).toPNG());
    out.appearance = await win.webContents.executeJavaScript('KB.appearanceState()');

    /* custom colour scheme + rounded corners */
    await win.webContents.executeJavaScript('KB.smokeCustom(), undefined');
    await delay(1400);
    fs.writeFileSync(path.join(smokeDir, 'smoke-custom.png'),
      (await win.webContents.capturePage()).toPNG());

    /* the full appearance settings panel */
    await win.webContents.executeJavaScript('KB.smokeSettingsAppearance(), undefined');
    await delay(700);
    fs.writeFileSync(path.join(smokeDir, 'smoke-appearance.png'),
      (await win.webContents.capturePage()).toPNG());
    await win.webContents.executeJavaScript('$("#shade").classList.remove("open"), undefined');

    /* left slide-out drawer */
    await win.webContents.executeJavaScript('settings.mode="dark",applyTheme(),openDrawer(),undefined');
    await delay(500);
    fs.writeFileSync(path.join(smokeDir, 'smoke-drawer.png'),
      (await win.webContents.capturePage()).toPNG());
    await win.webContents.executeJavaScript('closeDrawer(), undefined');

    /* Geek effect — matrix rain + typed text */
    await win.webContents.executeJavaScript('KB.smokeGeek(), undefined');
    await delay(1800);
    fs.writeFileSync(path.join(smokeDir, 'smoke-geek.png'),
      (await win.webContents.capturePage()).toPNG());
    await win.webContents.executeJavaScript('settings.mode="dark",applyTheme(),undefined');

    /* pinboard edit mode — per-widget styles + scale controls */
    await win.webContents.executeJavaScript('KB.smokeEdit(), undefined');
    await delay(900);
    fs.writeFileSync(path.join(smokeDir, 'smoke-editboard.png'),
      (await win.webContents.capturePage()).toPNG());
    await win.webContents.executeJavaScript('hudEdit=false,settings.hudStyle={},renderStart(),undefined');

    /* command palette (Ctrl+K) + bookmarks bar */
    await win.webContents.executeJavaScript('KB.smokeCmdK(), undefined');
    await delay(700);
    fs.writeFileSync(path.join(smokeDir, 'smoke-cmdk.png'),
      (await win.webContents.capturePage()).toPNG());
    await win.webContents.executeJavaScript('($("#cmdk")&&$("#cmdk").remove()), undefined');

    /* reader mode on a real article */
    await win.webContents.executeJavaScript('KB.smokeNav("https://en.wikipedia.org/wiki/Web_browser"), undefined');
    for (let i = 0; i < 60; i++){ await delay(250);
      const s = await win.webContents.executeJavaScript('KB.smokeState()');
      if (s && !s.loading && /wiki\/Web_browser/.test(s.url||'')) break; }
    await delay(500);
    await win.webContents.executeJavaScript('openReader(), undefined');
    await delay(1800);
    fs.writeFileSync(path.join(smokeDir, 'smoke-reader.png'),
      (await win.webContents.capturePage()).toPNG());
    out.reader = await win.webContents.executeJavaScript('(function(){var d=document.querySelector(".reader-body");return d?d.innerText.trim().length:0;})()');
    await win.webContents.executeJavaScript('closeReader(),KB.smokeNav("konekt://start"),undefined');

    /* workspaces — three colour-coded tab groups + switcher popover */
    out.workspaces = await win.webContents.executeJavaScript('KB.smokeWorkspaces()');
    await delay(500);
    fs.writeFileSync(path.join(smokeDir, 'smoke-workspaces.png'),
      (await win.webContents.capturePage()).toPNG());
    /* switch to Work — the tab strip should now show only Work's tabs */
    await win.webContents.executeJavaScript('closeWsPop(), switchWs("work"), undefined');
    await delay(500);
    out.workStrip = await win.webContents.executeJavaScript('document.querySelectorAll("#tabs .tab").length');
    fs.writeFileSync(path.join(smokeDir, 'smoke-workspaces2.png'),
      (await win.webContents.capturePage()).toPNG());
    /* reset to a single Main workspace + start page */
    await win.webContents.executeJavaScript('closeWsPop(); settings.workspaces=[{id:"main",name:"Main",color:"#a970ff"}]; wsSetActive("main"); tabs.forEach(t=>t.ws="main"); KB.smokeNav("konekt://start"); renderTabs(); undefined');

    /* account modal (sign in / create) */
    await win.webContents.executeJavaScript('kpanelToggle(false), KB.smokeAccount(), undefined');
    await delay(700);
    fs.writeFileSync(path.join(smokeDir, 'smoke-account.png'),
      (await win.webContents.capturePage()).toPNG());
    /* back to an opaque window before the guest capture — capturePage can
       stall on a transparent/acrylic window */
    await win.webContents.executeJavaScript('settings.mode="dark",applyTheme(),closeAccount(),undefined');
    await delay(600);

    const guest = require('electron').webContents.getAllWebContents()
      .find(c => c.getType() === 'webview');
    if (guest) {
      out.guestTitle = guest.getTitle();
      try {
        const img = await Promise.race([
          guest.capturePage(),
          new Promise((_r, rej) => setTimeout(() => rej(new Error('capture-timeout')), 5000))
        ]);
        fs.writeFileSync(path.join(smokeDir, 'smoke-guest.png'), img.toPNG());
      } catch { out.guestCapture = 'skipped'; }
    }
    out.ok = true;
  } catch (err) {
    out.ok = false;
    out.error = String(err && err.stack || err);
  }
  console.log('SMOKE ' + JSON.stringify(out));
  await delay(200);
  app.exit(out.ok ? 0 : 1);
}
