/* Runs under electron (nativeImage): scales icon.png into the launcher
   mipmap densities. Usage: electron scripts/make-android-icons.js */
'use strict';
const { app, nativeImage } = require('electron');
const fs = require('fs');
const path = require('path');

app.whenReady().then(() => {
  const root = path.join(__dirname, '..');
  const src = nativeImage.createFromPath(path.join(root, 'icon.png'));
  const sizes = { 'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192 };
  for (const [dpi, px] of Object.entries(sizes)) {
    const dir = path.join(root, 'android', 'res', 'mipmap-' + dpi);
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, 'ic_launcher.png'),
      src.resize({ width: px, height: px, quality: 'best' }).toPNG());
  }
  console.log('ICONS OK');
  app.exit(0);
});
