/* Portable win-x64 build:
   dist/KONEKT-Browser-win-x64/  = electron runtime + this app in resources/app,
   exe renamed and branded (icon + version strings) via rcedit.
   Prereqs: dist/icon.ico exists (scripts/make-ico.js), rcedit installed.
   Run with plain node: node scripts/build-dist.mjs */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const out = path.join(root, 'dist', 'KONEKT-Browser-win-x64');
const exe = path.join(out, 'KONEKT Browser.exe');

console.log('clean', out);
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });

console.log('copy electron runtime');
fs.cpSync(path.join(root, 'node_modules', 'electron', 'dist'), out, { recursive: true });
fs.renameSync(path.join(out, 'electron.exe'), exe);
fs.rmSync(path.join(out, 'resources', 'default_app.asar'), { force: true });

console.log('stage app');
const appDir = path.join(out, 'resources', 'app');
fs.mkdirSync(appDir, { recursive: true });
for (const f of ['main.js', 'preload.js', 'browser.html', 'package.json', 'icon.png', 'icon-192.png', 'README.md'])
  fs.copyFileSync(path.join(root, f), path.join(appDir, f));

console.log('brand exe');
const { rcedit } = await import('rcedit');
await rcedit(exe, {
  'version-string': {
    ProductName: 'KONEKT Browser',
    FileDescription: 'KONEKT Browser',
    CompanyName: 'NKO Intl. Foundation of Technological Research & Development',
    LegalCopyright: '© 2026 KONEKT',
    OriginalFilename: 'KONEKT Browser.exe',
    InternalName: 'konekt-browser'
  },
  'file-version': '1.1.0.0',
  'product-version': '1.1.0.0',
  icon: path.join(root, 'dist', 'icon.ico')
});

console.log('BUILD OK');
