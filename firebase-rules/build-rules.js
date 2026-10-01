// Regenerates database.rules.json from database.rules.src.js
//   node firebase-rules/build-rules.js
const fs = require('fs');
const path = require('path');

const rules = require('./database.rules.src.js');
const out = path.join(__dirname, 'database.rules.json');
fs.writeFileSync(out, JSON.stringify(rules, null, 2) + '\n');
console.log('Wrote', out);
