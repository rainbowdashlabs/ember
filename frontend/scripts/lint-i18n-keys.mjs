#!/usr/bin/env node
/**
 * i18n key linter for the Ember frontend.
 *
 * Two checks:
 *   1. Missing keys: every t('foo.bar') / $t('foo.bar') / te('foo.bar') call
 *      under src/ must resolve to a key defined in src/i18n/de-DE.ts or, for
 *      the helpCenter prefix, in src/i18n/de-DE.helpcenter.ts (merged into the
 *      locale at runtime under that prefix).
 *   2. Unused keys: every leaf key defined in src/i18n/de-DE.ts must be
 *      referenced at least once.
 *
 * Tunables:
 *   --check-unused=false   suppress the unused-key warnings
 *   --warn-unused          report unused keys at warn level (default)
 *   --error-unused         report unused keys at error level
 *
 * Heuristics:
 *   - Dynamic keys (template literals, string concatenation, computed keys)
 *     are skipped — there's no static analysis that resolves them
 *     reliably without an AST. Both ends of the check therefore err on
 *     the side of false negatives over false positives.
 *   - Keys that are themselves dynamic prefixes (e.g. `pages.${name}.title`)
 *     short-circuit to a "any leaf under this prefix counts" allow-list,
 *     so static usage detection doesn't flag every page title as unused.
 *   - Enum-backed sections: some i18n sections mirror a backend Java enum and
 *     are only ever referenced through dynamic keys, which the usage scan
 *     cannot resolve. Those sections are declared in ENUM_BACKED_SECTIONS and
 *     checked directly against the enum's constants: every constant must have
 *     all its leaf keys, and ALL_CAPS entries without a matching constant are
 *     reported as stale.
 */

import {readFileSync} from 'fs'
import {join} from 'path'
import {SRC, walk, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const {warn, error} = reporter
const CAT_MISSING = 'Missing i18n key'
const CAT_UNUSED = 'Unused i18n key'
const CAT_ENUM = 'Enum-backed i18n section'

const args = new Map()
for (const arg of process.argv.slice(2)) {
    if (arg === '--check-unused=false') args.set('check-unused', 'false')
    else if (arg === '--error-unused') args.set('unused-severity', 'error')
    else {
        const m = arg.match(/^--([\w-]+)=(.+)$/)
        if (m) args.set(m[1], m[2])
    }
}

const CHECK_UNUSED = (args.get('check-unused') ?? 'true') !== 'false'
const UNUSED_SEVERITY = args.get('unused-severity') ?? 'warn'

const I18N_FILE = `${SRC}/i18n/de-DE.ts`
const I18N_HELPCENTER_FILE = `${SRC}/i18n/de-DE.helpcenter.ts`
const HELPCENTER_PREFIX = 'helpCenter'

function collectKeys(text) {
    const start = text.indexOf('export default {')
    const body = start === -1 ? text : text.slice(start)
    const keys = new Set()
    const stack = []
    let i = 0
    while (i < body.length) {
        const ch = body[i]
        if (ch === '/' && body[i + 1] === '/') {
            while (i < body.length && body[i] !== '\n') i++
            continue
        }
        if (ch === '/' && body[i + 1] === '*') {
            i += 2
            while (i < body.length - 1 && !(body[i] === '*' && body[i + 1] === '/')) i++
            i += 2
            continue
        }
        if (ch === '{') { stack.push('?'); i++; continue }
        if (ch === '}') { stack.pop(); i++; continue }
        const propMatch = body.slice(i).match(/^([\w-]+|'(?:[^'\\]|\\.)+'|"(?:[^"\\]|\\.)+")\s*:/)
        if (!propMatch && (ch === '\'' || ch === '"' || ch === '`')) {
            const quote = ch
            i++
            while (i < body.length && body[i] !== quote) {
                if (body[i] === '\\') i++
                i++
            }
            i++
            continue
        }
        if (propMatch && stack.length > 0) {
            let propName = propMatch[1]
            if (propName.startsWith('\'') || propName.startsWith('"')) propName = propName.slice(1, -1)
            stack[stack.length - 1] = propName
            const afterColon = i + propMatch[0].length
            let j = afterColon
            while (j < body.length && /\s/.test(body[j])) j++
            if (body[j] !== '{') {
                keys.add(stack.filter(s => s !== '?').join('.'))
            }
            i = afterColon
            continue
        }
        i++
    }
    return keys
}

const definedKeys = collectKeys(readFileSync(I18N_FILE, 'utf-8'))
for (const key of collectKeys(readFileSync(I18N_HELPCENTER_FILE, 'utf-8'))) {
    definedKeys.add(`${HELPCENTER_PREFIX}.${key}`)
}

const usedExact = new Set()
const usedPrefixes = new Set()

const T_CALL = /\b(?:\$)?(?:t|te|tc)\s*\(\s*(['"`])([^'"`]+)\1\s*[,)]/g
const T_CALL_DYNAMIC_PREFIX = /\b(?:\$)?(?:t|te|tc)\s*\(\s*`([^`${}]+)\$\{/g
const T_CALL_CONCAT_PREFIX = /\b(?:\$)?(?:t|te|tc)\s*\(\s*(['"`])([^'"`]+)\1\s*\+/g

const files = [...walk(SRC, '.vue'), ...walk(SRC, '.ts')].filter(f => !f.endsWith('de-DE.ts'))

for (const file of files) {
    const text = readFileSync(file, 'utf-8')
    for (const m of text.matchAll(T_CALL)) {
        const key = m[2]
        if (!key.includes('.')) continue
        if (key.includes('${')) {
            const prefix = key.split('${')[0].replace(/\.+$/, '')
            if (prefix) usedPrefixes.add(prefix)
            continue
        }
        usedExact.add(key)
        if (!definedKeys.has(key)) {
            const offset = m.index ?? 0
            const line = text.slice(0, offset).split('\n').length
            error(file, line, `i18n key not defined: t('${key}')`, CAT_MISSING)
        }
    }
    for (const m of text.matchAll(T_CALL_DYNAMIC_PREFIX)) {
        const prefix = m[1].replace(/\.+$/, '')
        if (prefix.length > 0) usedPrefixes.add(prefix)
    }
    for (const m of text.matchAll(T_CALL_CONCAT_PREFIX)) {
        const prefix = m[2].replace(/\.+$/, '')
        if (prefix.length > 0) usedPrefixes.add(prefix)
    }
}

const REPO_ROOT = new URL('../..', import.meta.url).pathname

const ENUM_BACKED_SECTIONS = [
    {
        enumFile: 'src/main/java/dev/chojo/ember/api/auth/StationPermission.java',
        prefix: 'permissions',
        leaves: ['label', 'desc'],
    },
]

/**
 * Extracts the constant names of a Java enum: comments are stripped, the
 * constant list ends at the first semicolon, and names inside parentheses or
 * braces (constructor arguments, constant bodies) are ignored so that only
 * top-level declarations count.
 */
function parseJavaEnumConstants(text) {
    const stripped = text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*/g, '')
    const body = stripped.slice(stripped.indexOf('{') + 1).split(';')[0]
    const constants = new Set()
    let depth = 0
    for (const token of body.matchAll(/([A-Z][A-Z0-9_]*)|([({])|([)}])/g)) {
        if (token[2]) depth++
        else if (token[3]) depth--
        else if (depth === 0) constants.add(token[1])
    }
    return constants
}

for (const section of ENUM_BACKED_SECTIONS) {
    let text
    try {
        text = readFileSync(join(REPO_ROOT, section.enumFile), 'utf-8')
    } catch {
        error(I18N_FILE, 0, `enum file not found: ${section.enumFile}`, CAT_ENUM)
        continue
    }
    const constants = parseJavaEnumConstants(text)
    for (const constant of constants) {
        for (const leaf of section.leaves) {
            const key = `${section.prefix}.${constant}.${leaf}`
            if (!definedKeys.has(key)) {
                error(I18N_FILE, 0, `missing translation for enum value ${constant}: ${key}`, CAT_ENUM)
            }
        }
    }
    const stalePattern = new RegExp(`^${section.prefix}\\.([A-Z][A-Z0-9_]*)\\.`)
    const reported = new Set()
    for (const key of definedKeys) {
        const m = key.match(stalePattern)
        if (m && !constants.has(m[1]) && !reported.has(m[1])) {
            reported.add(m[1])
            warn(I18N_FILE, 0, `stale ${section.prefix} entry without matching enum value: ${m[1]}`, CAT_ENUM)
        }
    }
}

if (CHECK_UNUSED) {
    for (const key of definedKeys) {
        if (usedExact.has(key)) continue
        let matched = false
        for (const pfx of usedPrefixes) {
            if (key.startsWith(pfx + '.') || key.startsWith(pfx)) { matched = true; break }
        }
        if (matched) continue
        const msg = `i18n key defined but never referenced: ${key}`
        if (UNUSED_SEVERITY === 'error') error(I18N_FILE, 0, msg, CAT_UNUSED)
        else warn(I18N_FILE, 0, msg, CAT_UNUSED)
    }
}

console.log(`\n\x1b[1mi18n Key Check\x1b[0m (defined: ${definedKeys.size}, referenced: ${usedExact.size})`)
reporter.print()
reporter.exit()
