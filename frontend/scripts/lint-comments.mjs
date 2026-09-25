#!/usr/bin/env node
/**
 * Comment Linter
 *
 * Code documents itself in this project. Clear names, small functions and honest
 * structure carry the meaning, and a passage that needs a comment beside it to be
 * understood wants rewriting rather than annotating.
 *
 * So only doc comments are allowed: the block that introduces a class, a method,
 * a function, a record or a constant, and says what it is for. Everything else
 * goes. Where a rationale genuinely has to be written down it belongs in the doc
 * comment of the thing it is about, where the next reader will actually look.
 *
 * The one exception is a TODO marking a real gap: something left unimplemented or
 * knowingly missing. Those are better said than left silent.
 *
 * The rule was written down long before this check existed, and it was broken
 * again in every sitting, which is what a rule nobody checks is worth. Frontend
 * sources are always scanned; the Java sources above are scanned too when the
 * repository root is there, since the rule is the project's and not the
 * frontend's. The frontend image is built from `frontend/` alone, so their
 * absence is a warning rather than an error, the same way the em dash check
 * stands down.
 *
 * Exit code 1 if a comment is found that is neither a doc comment nor a TODO.
 */

import {existsSync, readFileSync, statSync, writeFileSync} from 'fs'
import {join, relative} from 'path'
import {SRC, walk, GREEN, YELLOW, RESET, BOLD, createReporter} from './lint-utils.mjs'

const reporter = createReporter()

/**
 * What each file was already carrying when the check was written.
 *
 * <p>The rule is older than the check, and by the time anybody counted, four thousand comments had
 * gathered. Failing on all of them would have meant either a rewrite nobody asked for or a check
 * switched off within the week, so the debt is written down instead and the rule bites from here:
 * a file may carry what it carried, and not one comment more. A file that is not in the list must
 * carry none at all, which is every file written from now on.
 *
 * <p>Run with `--update` after genuinely removing some, so the count can only ever fall.
 */
const BASELINE_PATH = new URL('./comment-baseline.json', import.meta.url).pathname
const UPDATING = process.argv.includes('--update')
const baseline = existsSync(BASELINE_PATH) ? JSON.parse(readFileSync(BASELINE_PATH, 'utf-8')) : {}
const counts = {}

const FRONTEND_EXTENSIONS = ['.ts', '.vue', '.js', '.mjs']

const ROOT_TARGETS = [
    {path: 'src/main/java', extensions: ['.java']},
    {path: 'src/test/java', extensions: ['.java']},
]

const REPO_ROOT = new URL('../..', import.meta.url).pathname
const FRONTEND_ROOT = new URL('..', import.meta.url).pathname

/**
 * What a file is called in the baseline, which has to be the one name in both layouts it is read
 * in.
 *
 * <p>The frontend image is built from `frontend/` alone, so there the frontend is the root and
 * naming a file relative to the repository above it names something else entirely. Every lookup
 * then missed, every file counted as one that may carry nothing, and the image failed on six
 * hundred comments the checkout was perfectly happy with. A frontend file is therefore named from
 * the frontend down, with the prefix written rather than derived.
 */
function baselineKey(file) {
    if (file.startsWith(FRONTEND_ROOT)) return join('frontend', relative(FRONTEND_ROOT, file))
    return relative(REPO_ROOT, file)
}

/**
 * A line that opens a block comment which is a doc comment, and so may stand.
 *
 * Java and TypeScript spell it the same way, and a licence header is a block
 * comment at the top of the file that every source here carries.
 */
const DOC_OPENER = /^\s*\/\*\*/
const BLOCK_OPENER = /^\s*\/\*/
const LICENCE_MARKER = /SPDX-License-Identifier/

const TODO = /^\s*(\/\/|\/\*|\*)\s*TODO\b/

/**
 * Whether a `//` sits inside a string or a regular expression rather than
 * starting a comment.
 *
 * Walking the line character by character is the only way to tell: an address in
 * a string, a protocol in a template literal and a character class in a regular
 * expression all carry two slashes and none of them is a comment.
 */
function commentStart(line) {
    let quote = null
    for (let i = 0; i < line.length; i++) {
        const c = line[i]
        if (quote) {
            if (c === '\\') i++
            else if (c === quote) quote = null
            continue
        }
        if (c === '"' || c === "'" || c === '`') {
            quote = c
            continue
        }
        if (c === '/' && line[i + 1] === '/') return i
        if (c === '/' && line[i + 1] === '*') return i
    }
    return -1
}

function check(file) {
    const lines = readFileSync(file, 'utf-8').split('\n')
    const key = baselineKey(file)
    const allowed = baseline[key] ?? 0
    const found = []
    let inBlock = false

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i]

        if (inBlock) {
            if (line.includes('*/')) inBlock = false
            continue
        }

        if (TODO.test(line)) continue

        if (DOC_OPENER.test(line)) {
            inBlock = !line.includes('*/')
            continue
        }

        if (BLOCK_OPENER.test(line) || LICENCE_MARKER.test(line)) {
            const licence = LICENCE_MARKER.test(line) || LICENCE_MARKER.test(lines[i + 1] ?? '')
            inBlock = !line.includes('*/')
            if (!licence) {
                found.push({
                    line: i + 1,
                    message: 'Block comment that is not a doc comment. Put the reasoning in the doc comment of'
                        + ' the class or function it belongs to, or rewrite the passage so it needs none.',
                })
            }
            continue
        }

        const start = commentStart(line)
        if (start === -1) continue
        if (line.slice(start).startsWith('/*')) {
            inBlock = !line.slice(start).includes('*/')
            found.push({line: i + 1, message: 'Inline block comment. Only doc comments are allowed.'})
            continue
        }
        found.push({
            line: i + 1,
            message: `Line comment at column ${start + 1}. Only doc comments and TODO markers are allowed;`
                + ' put the reasoning in the doc comment of the enclosing class or function.',
        })
    }

    if (found.length > 0) counts[key] = found.length

    if (found.length > allowed) {
        const over = found.slice(allowed)
        for (const one of over) {
            reporter.error(file, one.line, one.message)
        }
    }

}

const scanned = []
for (const extension of FRONTEND_EXTENSIONS) {
    scanned.push(...walk(SRC, extension))
}
scanned.forEach(check)

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

if (UPDATING) {
    const kept = Object.fromEntries(
        Object.entries(counts)
            .filter(([key, count]) => count <= (baseline[key] ?? Infinity))
            .sort(([a], [b]) => a.localeCompare(b)),
    )
    writeFileSync(BASELINE_PATH, `${JSON.stringify(kept, null, 2)}\n`)
    const before = Object.values(baseline).reduce((sum, n) => sum + n, 0)
    const after = Object.values(kept).reduce((sum, n) => sum + n, 0)
    console.log(`\n${GREEN}${BOLD}Comment baseline written.${RESET} ${after} left, ${before - after} fewer.\n`)
    process.exit(0)
}

const debt = Object.entries(counts)
    .filter(([key, count]) => Math.min(count, baseline[key] ?? 0) > 0)
    .reduce((sum, [key, count]) => sum + Math.min(count, baseline[key] ?? 0), 0)

if (reporter.errors.length === 0 && reporter.warnings.length === 0) {
    console.log(
        `\n${GREEN}${BOLD}Comment lint passed.${RESET} ${scanned.length + rootFiles} files checked.`
            + (debt > 0 ? ` ${YELLOW}${debt} comment(s) still owed from before the check existed.${RESET}` : '')
            + '\n',
    )
} else {
    reporter.print()
    reporter.exit()
}
