/**
 * Static checks for the website in web-panels/ (no browser needed):
 *   1. every inline <script> and every .js file parses
 *   2. every local href/src in the HTML points at a file that exists
 *   3. no page still uses window.alert / confirm / prompt (use ReplateUI.toast / confirmAction instead)
 * Run:  node scripts/check-web.js
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.join(__dirname, '..', 'web-panels');
const problems = [];

// Lists every file under a folder.
function walk(dir) {
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const full = path.join(dir, entry.name);
        return entry.isDirectory() ? walk(full) : [full];
    });
}

const files = walk(root);

// Check 1a: every .js file parses.
for (const file of files.filter((f) => f.endsWith('.js'))) {
    // env-config.js is generated per environment and may be absent in CI
    try { new vm.Script(fs.readFileSync(file, 'utf8'), { filename: file }); }
    catch (e) { problems.push(`syntax: ${path.relative(root, file)}: ${e.message}`); }
}

// Checks 1b, 2 and 3 for every page.
for (const file of files.filter((f) => f.endsWith('.html'))) {
    const rel = path.relative(root, file);
    const html = fs.readFileSync(file, 'utf8');

    (html.match(/<script>[\s\S]*?<\/script>/g) || []).forEach((block, i) => {
        const code = block.replace(/^<script>/, '').replace(/<\/script>$/, '');
        try { new vm.Script(code, { filename: `${rel}#script${i}` }); }
        catch (e) { problems.push(`syntax: ${rel} inline script ${i}: ${e.message}`); }
        if (/\b(?:alert|confirm|prompt)\s*\(/.test(code.replace(/ReplateUI\.\w+\(/g, ''))) {
            problems.push(`dialog: ${rel} still calls alert/confirm/prompt`);
        }
    });

    const refs = [...html.matchAll(/\b(?:href|src)="([^"]+)"/g)].map((m) => m[1]);
    for (const ref of refs) {
        // links built in JavaScript templates (`${...}`) are not real files
        if (/^(?:https?:|mailto:|tel:|data:|#|\/\/)/.test(ref) || ref.includes('${')) continue;
        const clean = ref.split('#')[0].split('?')[0];
        if (!clean) continue;
        const target = clean.startsWith('/') ? path.join(root, clean) : path.join(path.dirname(file), clean);
        // env-config.js is created from your own keys and is not in git
        if (clean.endsWith('env-config.js')) continue;
        if (!fs.existsSync(target)) problems.push(`missing file: ${rel} -> ${ref}`);
    }
}

if (problems.length) {
    console.error(problems.join('\n'));
    console.error(`\n${problems.length} problem(s) found`);
    process.exit(1);
}
console.log(`web check ok (${files.filter((f) => f.endsWith('.html')).length} pages, ${files.filter((f) => f.endsWith('.js')).length} scripts)`);
