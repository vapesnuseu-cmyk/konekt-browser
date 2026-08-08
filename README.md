# KONEKT BROWSER

A real web browser in the KONEKT design language — black, cubic, Montserrat.
NKO Intl. Foundation of Technological Research & Development.

**Download for Windows:** <https://konekt-browser.vercel.app> ·
[Releases](https://github.com/vapesnuseu-cmyk/konekt-browser/releases)

Chromium does the rendering (via Electron), so every site works exactly as it
does in Chrome. The chrome around it is one file, KONEKT-style: `browser.html`.

## Run it

Double-click **`KONEKT Browser.cmd`** — or from a terminal:

```bash
npm start
```

First time only (downloads Electron):

```bash
npm install
```

> `node` isn't on PATH on this machine — npm needs
> `C:\Users\kisel\AppData\Local\Programs\node-v24.18.1-win-x64` prefixed to PATH.
> The `.cmd` launcher doesn't need node at all.

## What's inside

| File | Role |
|---|---|
| `main.js` | Electron main process: window, downloads, permission prompts, popup→tab policy, native context menus, shortcut forwarding from pages |
| `browser.html` | The whole chrome UI — tabs, omnibox, start page, history, bookmarks, downloads, find bar, settings — one file, vanilla JS |
| `preload.js` | Tiny allow-listed IPC bridge for the chrome page |

## Features

Opera-inspired, KONEKT-designed:

- **Sidebar**: Speed Dial, KONEKT panel, bookmarks, history, downloads,
  ad-blocker shield with a live block counter, settings — one icon rail.
- **KONEKT side panel**: the full KONEKT app docked next to your tabs
  (same login/session as regular tabs) — messages while you browse.
- **Speed Dial**: add your own tiles with the + tile, remove on hover;
  top sites fill in automatically.
- **Ad & tracker blocker**: built in, on by default, domain-level list of
  pure adtech hosts (no login/CDN breakage), toggle in Settings.
- **Pop out video**: menu action floats the page's video in a
  picture-in-picture window.

And the browser core:

- **Tabs**: drag to reorder, middle-click to close, per-tab mute button,
  loading spinner, favicons, Ctrl+1…9 to jump, Ctrl+Tab to cycle.
- **Omnibox**: URL / search detection (Google, DuckDuckGo, Bing or Yandex —
  pick in Settings), history suggestions with keyboard navigation,
  padlock / not-secure indicator, one-click bookmark star.
- **Start page**: KONEKT wordmark, clock, search, top sites from your history,
  bookmark strip. The KONEKT app has a permanent tile.
- **History** (Ctrl+H): searchable, grouped by day. **Bookmarks**, **Downloads**
  (Ctrl+J) with pause/resume/cancel and show-in-folder.
- **Find in page** (Ctrl+F) with match counter. **Zoom** (Ctrl +/−/0 and
  Ctrl+wheel, per tab). **Print** (Ctrl+P). **DevTools** (F12). Fullscreen (F11).
- **Permission prompts**: camera/mic, location, notifications and friends show a
  KONEKT-style bar with an optional per-site remember.
- Popups open as tabs. Sessions restore on restart (toggle in Settings).
  Light theme in Settings. Site data lives in the `persist:konekt` partition;
  Settings → Clear browsing data wipes it.
- Pages see a clean Chrome user agent, so logins and sites that sniff for
  Electron behave.

Session state (tabs, history, bookmarks, settings) is stored locally in
`%APPDATA%\KONEKT Browser`.

## Smoke test

```bash
npm run smoke
```

Boots the app, screenshots the start page, loads example.com and wikipedia.org,
opens the History panel, screenshots again, prints a JSON verdict and exits 0/1.

## Keyboard map

| | |
|---|---|
| Ctrl+T / Ctrl+W | new / close tab |
| Ctrl+Shift+T | reopen closed tab |
| Ctrl+Tab / Ctrl+Shift+Tab | next / previous tab |
| Ctrl+L or F6 | focus address bar |
| Alt+← / Alt+→ | back / forward (mouse side buttons too) |
| Ctrl+R / F5, Ctrl+Shift+R | reload, hard reload |
| Ctrl+D | bookmark |
| Ctrl+F | find in page |
| Ctrl+H / Ctrl+J | history / downloads |
| Ctrl +/− / 0 | zoom / reset |
| F11 / F12 | fullscreen / devtools |

© 2026 KONEKT · NKO Intl. Foundation of Technological Research & Development
