/**
 * Checks the browser storage disclosure against the running code.
 *
 * The privacy policy and the consent text carry a generated section listing every value the
 * application keeps in the browser. That section is rendered from `src/main/resources/browser_storage.json`
 * in the backend, so a key added to the frontend without a matching entry would quietly make the
 * published disclosure wrong. This linter is what stops that: every key the frontend reads or writes
 * must be declared, and every declared key must still be used.
 *
 * Keys are collected from `localStorage.getItem/setItem/removeItem` and from the wrapper in
 * `api/storage`, which is the only other way the application reaches local storage.
 *
 * The catalog lives above `frontend/`. The Docker image builds from this directory alone, so when the
 * backend sources are absent the linter stands down with a warning instead of failing.
 */

import {existsSync, readFileSync} from 'fs'
import {join} from 'path'
import {SRC, createReporter, rel, walk} from './lint-utils.mjs'

const reporter = createReporter()
const {warn, error} = reporter

const REPO_ROOT = join(SRC, '../..')
const CATALOG_FILE = join(REPO_ROOT, 'src/main/resources/browser_storage.json')
const CATALOG_LABEL = 'src/main/resources/browser_storage.json'

const CAT_UNDECLARED = 'undeclared'
const CAT_STALE = 'stale'
const CAT_DYNAMIC = 'dynamic'

const STORAGE_METHODS = 'getItem|setItem|removeItem'
const STORAGE_MODULE = /from\s+['"][~@]\/api\/storage['"]/
/** Identifiers that are the wrapper's own parameters rather than a key. */
const PARAMETER_NAMES = new Set(['key'])

/**
 * Collects `const NAME = 'value'` bindings so a key held in a constant resolves to its literal.
 */
function constantsIn(content) {
    const constants = new Map()
    const pattern = /const\s+([A-Za-z_$][\w$]*)\s*=\s*'([^']*)'/g
    let match
    while ((match = pattern.exec(content)) !== null) {
        constants.set(match[1], match[2])
    }
    return constants
}

/**
 * Returns every storage key the given file reads or writes, plus the arguments that could not
 * be resolved to a literal.
 */
function keysIn(file, content) {
    const constants = constantsIn(content)
    const usesWrapper = STORAGE_MODULE.test(content)
    const keys = new Set()
    const dynamic = []

    const qualified = new RegExp(`(\\w+)\\??\\.(?:${STORAGE_METHODS})\\(\\s*([^,)]+)`, 'g')
    const bare = new RegExp(`(^|[^.\\w])(?:${STORAGE_METHODS})\\(\\s*([^,)]+)`, 'g')

    const record = argument => {
        const literal = argument.trim().match(/^'([^']*)'$/)
        if (literal) {
            keys.add(literal[1])
            return
        }
        const identifier = argument.trim()
        if (PARAMETER_NAMES.has(identifier)) return
        if (constants.has(identifier)) {
            keys.add(constants.get(identifier))
            return
        }
        dynamic.push(identifier)
    }

    let match
    while ((match = qualified.exec(content)) !== null) {
        if (match[1] !== 'localStorage') continue
        record(match[2])
    }
    if (usesWrapper) {
        while ((match = bare.exec(content)) !== null) {
            record(match[2])
        }
    }

    return {keys, dynamic}
}

if (!existsSync(CATALOG_FILE)) {
    warn(
        CATALOG_LABEL,
        0,
        'backend sources are not in this checkout — the browser storage disclosure cannot be cross-checked '
        + 'against the code, so this check stands down',
        CAT_UNDECLARED,
    )
    reporter.print()
    process.exit(reporter.errors.length > 0 ? 1 : 0)
}

const catalog = JSON.parse(readFileSync(CATALOG_FILE, 'utf-8'))
const declared = new Set((catalog.entries ?? []).map(entry => entry.key))

const used = new Map()
for (const file of [...walk(SRC, '.ts'), ...walk(SRC, '.vue')]) {
    if (file.endsWith('.test.ts')) continue
    const content = readFileSync(file, 'utf-8')
    const {keys, dynamic} = keysIn(file, content)
    for (const key of keys) {
        if (!used.has(key)) used.set(key, file)
    }
    for (const identifier of dynamic) {
        warn(
            file,
            0,
            `storage key is not a literal (${identifier}) — it cannot be checked against ${CATALOG_LABEL}`,
            CAT_DYNAMIC,
        )
    }
}

for (const [key, file] of used) {
    if (!declared.has(key)) {
        error(
            file,
            0,
            `local storage key '${key}' is not declared in ${CATALOG_LABEL} — the published storage `
            + 'disclosure would not mention it',
            CAT_UNDECLARED,
        )
    }
}

for (const key of declared) {
    if (!used.has(key)) {
        warn(
            CATALOG_LABEL,
            0,
            `declared local storage key '${key}' is no longer used by the frontend`,
            CAT_STALE,
        )
    }
}

console.log(`Checked ${used.size} storage keys against ${declared.size} declared in ${rel(CATALOG_FILE)}`)
reporter.print()
process.exit(reporter.errors.length > 0 ? 1 : 0)
