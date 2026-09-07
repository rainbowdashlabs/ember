#!/usr/bin/env node
/**
 * Em Dash Linter
 *
 * The em dash is forbidden in this project. It is not a typography preference:
 * the rule exists so the text the application ships reads the same everywhere,
 * and a rule nobody checks is a rule that comes back.
 *
 * Frontend sources are always checked. The repository root is checked as well
 * when it is there, because Java doc comments, the changelog and the concept
 * documents are just as covered by the rule. The frontend image is built from
 * `frontend/` alone, so their absence is not an error: the check stands down
 * with a warning instead, the same way the i18n cross-checks do.
 *
 * The migrations under src/main/resources/database are exempt. Their column
 * comments live in databases that have long since run them, so rewriting the
 * files would only make a fresh install differ from every existing one.
 *
 * Exit code 1 if the character is found anywhere it is not allowed.
 */

import {existsSync, readFileSync, statSync} from 'fs'
import {join} from 'path'
import {SRC, walk, rel, RED, GREEN, YELLOW, RESET, BOLD, createReporter} from './lint-utils.mjs'

const reporter = createReporter()

/** Built from its code point so this file does not trip over itself. */
const EM_DASH = String.fromCharCode(0x2014)

/**
 * The names markup gives the same character, assembled here for the same reason.
 *
 * <p>A page can name the dash instead of carrying it, and it then reaches the reader as the dash
 * while the file itself holds none. A check that looks only for the character passes such a page,
 * which is how seventeen of them gathered before anybody noticed.
 */
const ENTITIES = ['mdash', '#8212', '#x2014', '#X2014'].map(name => `&${name};`)

const SPELLINGS = [EM_DASH, ...ENTITIES]

/** What is scanned inside the frontend, which is always present. */
const FRONTEND_EXTENSIONS = ['.ts', '.vue', '.js', '.mjs']

/**
 * What is scanned above the frontend when the full repository is checked out.
 * Paths are relative to the repository root.
 */
const ROOT_TARGETS = [
    {path: 'src/main/java', extensions: ['.java']},
    {path: 'src/test/java', extensions: ['.java']},
    {path: 'src/main/resources/i18n', extensions: ['.json']},
    {path: 'templates', extensions: ['.md', '.html']},
    {path: 'CHANGELOG.md', extensions: null},
]

const REPO_ROOT = new URL('../..', import.meta.url).pathname

function check(file) {
    const lines = readFileSync(file, 'utf-8').split('\n')
    for (let i = 0; i < lines.length; i++) {
        for (const spelling of SPELLINGS) {
            const column = lines[i].indexOf(spelling)
            if (column === -1) continue
            reporter.error(
                file,
                i + 1,
                `Em dash at column ${column + 1}. Use a comma, a colon, parentheses, a full stop or a spaced hyphen instead.`,
            )
            break
        }
    }
}

// ── The frontend, which is always there ─────────────────────────────

const scanned = []
for (const extension of FRONTEND_EXTENSIONS) {
    scanned.push(...walk(SRC, extension))
}
scanned.push(...walk(new URL('.', import.meta.url).pathname, '.mjs'))
scanned.forEach(check)

// ── The rest of the repository, when this is not the frontend image ──

let rootFiles = 0
const missing = []
for (const target of ROOT_TARGETS) {
    const absolute = join(REPO_ROOT, target.path)
    if (!existsSync(absolute)) {
        missing.push(target.path)
        continue
    }
    const files = statSync(absolute).isDirectory()
        ? target.extensions.flatMap(extension => walk(absolute, extension))
        : [absolute]
    files.forEach(check)
    rootFiles += files.length
}

if (missing.length === ROOT_TARGETS.length) {
    reporter.warn(
        '',
        0,
        'Only the frontend was checked: the repository root is not present, which is expected inside the frontend image.',
    )
}

// ── Output ──────────────────────────────────────────────────────────

if (reporter.errors.length === 0 && reporter.warnings.length === 0) {
    console.log(`\n${GREEN}${BOLD}Em dash lint passed.${RESET} ${scanned.length + rootFiles} files carry none.\n`)
} else {
    reporter.print()
    reporter.exit()
}
