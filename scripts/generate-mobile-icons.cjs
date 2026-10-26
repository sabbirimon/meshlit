#!/usr/bin/env node
// Requires sharp. Adaptive vector layers live in Android drawable resources.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
async function main() {
  const source = fs.readFileSync(path.join(root, 'ohosApp/AppScope/resources/base/media/app_icon.svg'), 'utf8');
  for (const [density, size] of Object.entries({mdpi:48, hdpi:72, xhdpi:96, xxhdpi:144, xxxhdpi:192})) {
    const directory = path.join(root, `app/src/main/res/mipmap-${density}`);
    for (const round of [false, true]) {
      const svg = Buffer.from(round ? source.replace('rx="30"', 'rx="64"') : source);
      await sharp(svg, {density:216}).resize(size,size).webp({lossless:true})
        .toFile(path.join(directory, `ic_launcher${round ? '_round' : ''}.webp`));
    }
  }
}
main().catch(e => { console.error(e.message); process.exit(1); });
