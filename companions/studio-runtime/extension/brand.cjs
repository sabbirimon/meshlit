'use strict';
const fs = require('node:fs');
const path = require('node:path');

const file = path.join(__dirname, 'media', 'meshlit.svg');
const markup = fs.readFileSync(file, 'utf8')
    .replace('<svg ', '<svg aria-hidden="true" focusable="false" ');

module.exports = { file, markup };
