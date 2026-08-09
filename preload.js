/* KONEKT Browser — preload bridge for the chrome UI.
   The chrome page is trusted (it ships with the app) but still runs with
   contextIsolation; everything it may do in the main process goes through
   this small allow-listed surface. */
'use strict';
const { contextBridge, ipcRenderer } = require('electron');

const SEND = new Set([
  'perm-response',   // { id, allow, remember, origin, permission }
  'dl-action',       // { id, action: 'pause'|'resume'|'cancel' }
  'shell-show',      // path — reveal in Explorer
  'shell-open',      // path — open with default app
  'win-fullscreen',  // toggle window fullscreen (F11)
  'set-theme',       // { dark } — recolour the native window buttons
  'set-material',    // { glass } — Windows acrylic for Liquid Glass mode
  'clear-data',      // wipe cookies/storage/cache of the web partition
  'adblock-set',     // { on } — toggle the built-in ad/tracker blocker
  'chrome-ready'     // chrome finished booting (smoke mode waits for this)
]);
const ON = new Set([
  'open-url',        // (url, background) — popup/second-instance wants a tab
  'chord',           // forwarded keyboard shortcut from a guest page
  'zoom-step',       // (+1|-1) ctrl+wheel zoom from a guest page
  'dl',              // download lifecycle events
  'perm-request',    // { id, origin, permission }
  'fullscreen',      // (bool) window fullscreen state changed
  'adblock-count'    // running total of blocked requests this session
]);

contextBridge.exposeInMainWorld('konektBridge', {
  send(ch, data) { if (SEND.has(ch)) ipcRenderer.send(ch, data); },
  on(ch, fn) { if (ON.has(ch)) ipcRenderer.on(ch, (_e, ...a) => fn(...a)); },
  info() { return ipcRenderer.invoke('app-info'); }
});
