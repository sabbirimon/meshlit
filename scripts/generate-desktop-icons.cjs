#!/usr/bin/env node
// Requires sharp. Reuses the existing Meshlit vector mark; no new artwork.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
async function main() {
  const source = fs.readFileSync(path.join(root, 'ohosApp/AppScope/resources/base/media/app_icon.svg'));
  const sizes = [16, 32, 48, 64, 128, 256, 512, 1024];
  const png = new Map();
  for (const size of sizes) png.set(size, await sharp(source, { density: 768 }).resize(size, size).png().toBuffer());
  const out = path.join(root, 'desktopApp/distribution/icons');
  fs.mkdirSync(out, { recursive: true });
  fs.writeFileSync(path.join(out, 'meshlit.png'), png.get(1024));
  const resource = path.join(root, 'desktopApp/src/main/resources/icons');
  fs.mkdirSync(resource, { recursive: true });
  fs.writeFileSync(path.join(resource, 'meshlit.png'), png.get(256));
  const icoSizes = sizes.filter(s => s <= 256);
  const header = Buffer.alloc(6 + 16 * icoSizes.length);
  header.writeUInt16LE(1, 2); header.writeUInt16LE(icoSizes.length, 4);
  let offset = header.length;
  icoSizes.forEach((size, i) => {
    const p = 6 + i * 16;
    header[p] = header[p + 1] = size === 256 ? 0 : size;
    header.writeUInt16LE(1, p + 4); header.writeUInt16LE(32, p + 6);
    header.writeUInt32LE(png.get(size).length, p + 8); header.writeUInt32LE(offset, p + 12);
    offset += png.get(size).length;
  });
  fs.writeFileSync(path.join(out, 'meshlit.ico'), Buffer.concat([header, ...icoSizes.map(s => png.get(s))]));
  const chunks = [[16,'icp4'],[32,'icp5'],[64,'icp6'],[128,'ic07'],[256,'ic08'],[512,'ic09'],[1024,'ic10']].map(([size,type]) => {
    const h = Buffer.alloc(8); h.write(type); h.writeUInt32BE(8 + png.get(size).length, 4);
    return Buffer.concat([h, png.get(size)]);
  });
  const h = Buffer.alloc(8); h.write('icns'); h.writeUInt32BE(8 + chunks.reduce((n,c) => n + c.length, 0), 4);
  fs.writeFileSync(path.join(out, 'meshlit.icns'), Buffer.concat([h, ...chunks]));
}
main().catch(e => { console.error(e.message); process.exit(1); });
