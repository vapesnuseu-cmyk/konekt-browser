# KONEKT BROWSER

A real web browser in the KONEKT design language — black, cubic, Montserrat.
NKO Intl. Foundation of Technological Research & Development.

**Download (Windows &amp; Android):** <https://konekt-browser.vercel.app> ·
[Releases](https://github.com/vapesnuseu-cmyk/konekt-browser/releases)

Two builds, one design language:

- **Desktop** (`main.js` + `browser.html`): Chromium via Electron, so every site
  renders exactly as in Chrome. The chrome is one file, KONEKT-style.
- **Android** (`android/`): a framework-only native app (no Gradle, no androidx)
  around the system WebView — tabs, a thumb-first **bottom** bar (address + lock +
  reload down low, five-icon nav, everything else in the menu), the Speed Dial
  start page, the same ad blocker. Tiny APK because the engine is the OS WebView.
  Build with `scripts/build-android.ps1` → `dist/KONEKT-Browser-android.apk`.

### New in 1.1

- **Accounts + sync** — create a KONEKT Browser account (own backend: Vercel
  functions in `api/`, `lib/kb.js`, on the same Upstash Redis as KONEKT, keys
  namespaced `kb:`). Bookmarks, Speed Dial, history and settings sync across
  desktop and phone. Bearer-token auth; passwords are PBKDF2-SHA512.
- **Customisation** — Dark / Light / **Liquid Glass** modes, seven accent
  colours, six wallpapers. Desktop uses Windows 11 acrylic behind frosted chrome;
  Android uses a translucent theme. Compact layout density on desktop.
- **In-app updates** — Settings → *Check for updates* compares your build to the
  latest GitHub release and links straight to the download.

### New in 1.2 — full appearance parity with KONEKT

The browser now mirrors KONEKT's own appearance settings:

- **Colour scheme**: Dark / Light / System / Liquid Glass / **Custom**.
- **Custom colours** (desktop: Background, Outlines, Text; Android: a curated
  background palette) — the greys are derived from the background by perceived
  brightness so text stays legible, with a low-contrast warning.
- **Accent** swatches plus a full custom-colour picker (desktop).
- **Text size** (S / M / L), **Corner rounding** (0–24px, cubic by default),
  **Uppercase interface** toggle, **Reduce motion**, **Round avatar**, layout
  **density**, and the six **wallpapers**. Everything syncs across devices and a
  **Reset appearance** returns to the original KONEKT look.

### New in 1.3

- **Pinboard HUD** — the new-tab page is a drag-and-resize widget board (snap
  grid). Widgets: clock, search, Speed Dial, live **weather** (Open-Meteo),
  **news** (Hacker News), **markets** (CoinGecko), notes, bookmarks, reading
  list. Enable/arrange in *Edit board*; layout syncs to your account. No API keys.
- **RGB colour wheel** — a real HSV wheel + brightness for accent and custom
  colours, on desktop (canvas) and Android (native view).
- **Geek theme** — a `cmatrix` rain background with typed-out text, phosphor green.
- **Custom wallpaper** — paste an image, GIF, or looping MP4/WEBM URL.
- **Self-update on both platforms** — checks GitHub on every launch; if a newer
  version exists it asks, then downloads and installs in place (desktop swaps the
  portable folder and relaunches; Android opens the packaged installer).
- **Left slide-out menu** on desktop, plus reading list, screenshot, translate
  and copy-link. Sync merges per-key, so a phone push never clobbers desktop HUD.

### New in 1.4

- **Command palette** (Ctrl+K) — fuzzy search across open tabs, bookmarks,
  history and commands; arrow-key navigate, Enter to act.
- **Bookmarks bar** — optional strip under the toolbar (Settings, or Ctrl+Shift+B);
  middle-click opens in a new tab, right-click removes.
- **Tab right-click menu** — duplicate, reload, mute, close, close others,
  close to the right.
- **Fully free pinboard** — the HUD board spans the full width on an 8-column
  grid (no forced centre); **± SIZE** scales every pin, each pin has its own
  resize handle, and the droplet button cycles a per-widget look
  (Default / Glass / Solid / Clear / Accent). Layout, styles and scale sync.
- Fixed a 1.3.0 regression where the slide-out drawer hid the left icon rail.

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
