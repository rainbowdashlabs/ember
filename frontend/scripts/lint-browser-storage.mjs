/**
 * Checks the browser storage disclosure against the running code.
 *
 * The privacy policy and the consent text carry a generated section listing every value the
 * application keeps in the browser. That section is rendered from `src/main/resources/browser_storage.json`
 * in the backend, so a key added to the frontend without a matching entry would quietly make the
 * published disclosure wrong. This linter is what stops that: every key the frontend reads or writes
 * must be declared, and every declared key must still be used.
 *
 * Keys are collected from `localStorage` and `sessionStorage` (`getItem/setItem/removeItem`) and from
 * the wrapper in `api/storage`, which is the only other way the application reaches either. A key the
 * wrapper's tab functions (`getTabItem/setTabItem/removeTabItem`) or `sessionStorage` reach must be
 * declared with kind `SESSION_STORAGE`, every other key without it, so the disclosure names the right
 * storage.
 *
 * The cookies the server sets are catalogued too, as entries of kind `COOKIE`. The page writes none
 * of them, so they are held against the wrapper's `COOKIE_NECESSITY` map rather than against usage.
 */

import {readFileSync} from 'fs'
import {join} from 'path'
import {SRC, createReporter, rel, walk} from './lint-utils.mjs'

const reporter = createReporter()
const {warn, error} = reporter

const REPO_ROOT = join(SRC, '../..')
const CATALOG_FILE = join(REPO_ROOT, 'src/main/resources/browser_storage.json')
const CATALOG_LABEL = 'src/main/resources/browser_storage.json'
const NECESSITY_FILE = join(SRC, 'api/storage.ts')
const NECESSITY_LABEL = 'src/api/storage.ts'

const CAT_UNDECLARED = 'undeclared'
const CAT_NECESSITY = 'necessity'
const CAT_STALE = 'stale'
const CAT_DYNAMIC = 'dynamic'
const CAT_KIND = 'kind'

const STORAGE_METHODS = 'getItem|setItem|removeItem'
const TAB_METHODS = 'getTabItem|setTabItem|removeTabItem'
const LOCAL_KIND = 'LOCAL_STORAGE'
const SESSION_KIND = 'SESSION_STORAGE'
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
 * Returns every storage key the given file reads or writes with the kind of storage it reaches, plus
 * the arguments that could not be resolved to a literal.
 */
function keysIn(file, content) {
    const constants = constantsIn(content)
    const usesWrapper = STORAGE_MODULE.test(content)
    const keys = new Map()
    const dynamic = []

    const qualified = new RegExp(`(\\w+)\\??\\.(?:${STORAGE_METHODS})\\(\\s*([^,)]+)`, 'g')
    const bare = new RegExp(`(^|[^.\\w])(${STORAGE_METHODS}|${TAB_METHODS})\\(\\s*([^,)]+)`, 'g')
    const tabMethod = new RegExp(`^(?:${TAB_METHODS})$`)

    const record = (argument, kind) => {
        const literal = argument.trim().match(/^'([^']*)'$/)
        if (literal) {
            keys.set(literal[1], kind)
            return
        }
        const identifier = argument.trim()
        if (PARAMETER_NAMES.has(identifier)) return
        if (constants.has(identifier)) {
            keys.set(constants.get(identifier), kind)
            return
        }
        dynamic.push(identifier)
    }

    let match
    while ((match = qualified.exec(content)) !== null) {
        if (match[1] === 'localStorage') record(match[2], LOCAL_KIND)
        else if (match[1] === 'sessionStorage') record(match[2], SESSION_KIND)
    }
    if (usesWrapper) {
        while ((match = bare.exec(content)) !== null) {
            record(match[3], tabMethod.test(match[2]) ? SESSION_KIND : LOCAL_KIND)
        }
    }

    return {keys, dynamic}
}

const catalog = JSON.parse(readFileSync(CATALOG_FILE, 'utf-8'))
const storedEntries = (catalog.entries ?? []).filter(entry => entry.kind !== 'COOKIE')
const cookieEntries = (catalog.entries ?? []).filter(entry => entry.kind === 'COOKIE')
const declared = new Set(storedEntries.map(entry => entry.key))
const declaredNecessity = new Map(storedEntries.map(entry => [entry.key, entry.necessity]))
const declaredKind = new Map(storedEntries.map(entry => [entry.key, entry.kind ?? LOCAL_KIND]))

/**
 * Reads a necessity map out of the storage wrapper: `NECESSITY` decides at runtime whether a value
 * may be written, `COOKIE_NECESSITY` names the cookies the server sets. Both have to agree with the
 * catalog the published disclosure comes from.
 */
function runtimeNecessity(name) {
    const content = readFileSync(NECESSITY_FILE, 'utf-8')
    const block = content.match(new RegExp(`const ${name}: Record<string, StorageNecessityName> = \\{([\\s\\S]*?)\\n\\}`))
    if (!block) return null
    const map = new Map()
    for (const line of block[1].split('\n')) {
        const entry = line.match(/^\s*'?([\w.-]+)'?:\s*StorageNecessity\.(\w+),/)
        if (entry) map.set(entry[1], entry[2])
    }
    return map
}

/** Holds the cookies the wrapper names against the cookie entries of the catalog, both ways. */
function checkCookies() {
    const cookies = runtimeNecessity('COOKIE_NECESSITY')
    if (cookies === null) {
        error(NECESSITY_LABEL, 0, 'the COOKIE_NECESSITY map could not be read - cookies cannot be checked',
            CAT_NECESSITY)
        return
    }
    const declaredCookies = new Map(cookieEntries.map(entry => [entry.key, entry.necessity]))
    for (const [name, necessity] of cookies) {
        const expected = declaredCookies.get(name)
        if (expected !== necessity) {
            error(NECESSITY_LABEL, 0,
                `cookie '${name}' is ${necessity} here and ${expected ?? 'undeclared'} in ${CATALOG_LABEL}`,
                CAT_NECESSITY)
        }
    }
    for (const name of declaredCookies.keys()) {
        if (!cookies.has(name)) {
            error(NECESSITY_LABEL, 0, `declared cookie '${name}' is missing from COOKIE_NECESSITY`, CAT_NECESSITY)
        }
    }
}

checkCookies()

const runtime = runtimeNecessity('NECESSITY')
if (runtime === null) {
    error(NECESSITY_LABEL, 0, 'the NECESSITY map could not be read - storage consent cannot be checked',
        CAT_NECESSITY)
} else {
    for (const [key, necessity] of runtime) {
        const expected = declaredNecessity.get(key)
        if (expected === undefined) {
            error(NECESSITY_LABEL, 0,
                `key '${key}' carries a necessity but is not declared in ${CATALOG_LABEL}`, CAT_NECESSITY)
        } else if (expected !== necessity) {
            error(NECESSITY_LABEL, 0,
                `key '${key}' is ${necessity} here and ${expected} in ${CATALOG_LABEL} - consent would be `
                + 'asked for one thing and enforced for another', CAT_NECESSITY)
        }
    }
    for (const [key, necessity] of declaredNecessity) {
        if (!runtime.has(key)) {
            error(NECESSITY_LABEL, 0,
                `declared key '${key}' (${necessity}) has no necessity in the storage wrapper - it would `
                + 'never be written', CAT_NECESSITY)
        }
    }
}

const used = new Map()
for (const file of [...walk(SRC, '.ts'), ...walk(SRC, '.vue')]) {
    if (file.endsWith('.test.ts')) continue
    const content = readFileSync(file, 'utf-8')
    const {keys, dynamic} = keysIn(file, content)
    for (const [key, kind] of keys) {
        if (!used.has(key)) used.set(key, file)
        const expected = declaredKind.get(key)
        if (expected !== undefined && expected !== kind) {
            error(file, 0, `storage key '${key}' is used in ${kind} here and declared as ${expected} in `
                + `${CATALOG_LABEL} - the disclosure would name the wrong storage`, CAT_KIND)
        }
    }
    for (const identifier of dynamic) {
        warn(
            file,
            0,
            `storage key is not a literal (${identifier}) - it cannot be checked against ${CATALOG_LABEL}`,
            CAT_DYNAMIC,
        )
    }
}

for (const [key, file] of used) {
    if (!declared.has(key)) {
        error(
            file,
            0,
            `storage key '${key}' is not declared in ${CATALOG_LABEL} - the published storage `
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
            `declared storage key '${key}' is no longer used by the frontend`,
            CAT_STALE,
        )
    }
}

console.log(`Checked ${used.size} storage keys against ${declared.size} declared in ${rel(CATALOG_FILE)}`)
reporter.print()
process.exit(reporter.errors.length > 0 ? 1 : 0)
