/* Runs under electron (needs nativeImage): resizes icon.png to 256px and
   wraps the PNG bytes in a single-entry .ico — the format Windows Vista+
   reads natively for 256×256 entries.
   Usage: electron scripts/make-ico.js  → writes dist/icon.ico */
'use strict';
const { app, nativeImage } = require('electron');
const fs = require('fs');
const path = require('path');

function icoFromPng(png) {
  const h = Buffer.alloc(22);
  h.writeUInt16LE(0, 0);            // reserved
  h.writeUInt16LE(1, 2);            // type: icon
  h.writeUInt16LE(1, 4);            // one image
  h[6] = 0; h[7] = 0;               // 256 wide (0 = 256), 256 tall
  h[8] = 0; h[9] = 0;               // colors, reserved
  h.writeUInt16LE(1, 10);           // planes
  h.writeUInt16LE(32, 12);          // bpp
  h.writeUInt32LE(png.length, 14);  // payload size
  h.writeUInt32LE(22, 18);          // payload offset
  return Buffer.concat([h, png]);
}

app.whenReady().then(() => {
  const root = path.join(__dirname, '..');
  const img = nativeImage.createFromPath(path.join(root, 'icon.png'))
    .resize({ width: 256, height: 256, quality: 'best' });
  const png = img.toPNG();
  fs.mkdirSync(path.join(root, 'dist'), { recursive: true });
  fs.writeFileSync(path.join(root, 'dist', 'icon.ico'), icoFromPng(png));
  console.log('ICO ' + png.length + ' bytes');
  app.exit(0);
});
