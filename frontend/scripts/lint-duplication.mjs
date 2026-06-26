#!/usr/bin/env node
/**
 * Code repetition linter for the Ember frontend.
 *
 * Walks every .vue and .ts file under src/, takes overlapping windows of
 * --window=N lines (default 8), normalises each line, and reports windows
 * whose fingerprint repeats across at least --min-files=M (default 2)
 * different files. Within a single file, the same fingerprint is only
 * counted once so legitimate per-iteration repeats don't drown the report.
 *
 * Normalisation collapses string literals to "", numbers to 0, identifiers
 * three characters or longer to a single sigil, and whitespace to one
 * space. That keeps copy-pasted blocks recognisable across files that
 * renamed variables or tweaked literals, but ignores typing differences.
 *
 * Tunables (CLI flags or env):
 *   --window=N      window size in lines  (default 8, env LINT_DUP_WINDOW)
 *   --min-files=M   only report when reused in >= M files (default 2)
 *   --warn=N        warn for windows with >= N occurrences (default 2)
 *   --error=N       error for windows with >= N occurrences (default 4)
 *
 * The check exempts files inside src/components/ — extracting *into*
 * components is the goal, so reuse showing up in component libraries is
 * fine and expected. Lines that are mostly braces, imports, exports,
 * HTML tag boundaries or other boilerplate are skipped per-window so
 * trivially repeating import blocks don't fire.
 */

import {readFileSync} from 'fs'
import {SRC, walk, rel, isInsideComponents, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const {warn, error} = reporter
const CATEGORY = 'Code repetition'

const args = new Map()
for (const arg of process.argv.slice(2)) {
    const m = arg.match(/^--([\w-]+)=(.+)$/)
    if (m) args.set(m[1], m[2])
}

const WINDOW = Number(args.get('window') ?? process.env.LINT_DUP_WINDOW ?? 8)
const MIN_FILES = Number(args.get('min-files') ?? process.env.LINT_DUP_MIN_FILES ?? 2)
const WARN_AT = Number(args.get('warn') ?? process.env.LINT_DUP_WARN ?? 2)
const ERROR_AT = Number(args.get('error') ?? process.env.LINT_DUP_ERROR ?? 4)

const BOILERPLATE_LINE = new RegExp([
    '^\\s*(',
    'import\\b|export\\b|from\\b',
    '|\\}|\\{|<\\/?[\\w-]+',
    '|\\/\\/|\\/\\*|\\*|<!--|--',
    '|const\\s+\\{?[\\w,\\s]+\\}?\\s*=\\s*(ref|computed|reactive|shallowRef|toRef|toRefs|use[A-Z]\\w*|inject|defineModel)\\b',
    '|const\\s+\\w+\\s*=\\s*ref<',
    '|defineProps|defineEmits|defineExpose|defineSlots',
    '|onMounted|onBeforeUnmount|onBeforeMount|onUnmounted|watch\\(|watchEffect\\(',
    '|:[\\w-]+(:[\\w-]+)?\\s*=',
    '|@[\\w-]+(\\.[\\w]+)*\\s*=',
    '|v-[\\w-]+(:[\\w-]+)?\\b',
    ').*$|^.{0,15}$',
].join(''))

/**
 * Marks lines that sit inside a `defineProps<{ … }>()` or `defineEmits<{ … }>()` macro body.
 * The declaration shapes are part of Vue's compile-time surface area — they exist precisely to give
 * each component a typed contract, and lifting them into a shared file would defeat that purpose.
 * Standalone `interface` / `type` declarations are intentionally NOT silenced — when two files
 * declare the same shape there, it's real duplication and should be extracted into a shared type.
 */
function markMacroBodyLines(lines) {
    const inMacro = new Array(lines.length).fill(false)
    let depth = 0
    let active = false
    const opener = /\b(defineProps|defineEmits)\s*<\s*\{/
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i]
        let j = 0
        while (j < line.length) {
            if (!active) {
                const remainder = line.slice(j)
                const m = remainder.match(opener)
                if (!m) break
                const start = j + (m.index ?? 0) + m[0].length
                active = true
                depth = 1
                j = start
                continue
            }
            const ch = line[j]
            if (ch === '{') depth++
            else if (ch === '}') {
                depth--
                if (depth === 0) {
                    active = false
                    j++
                    continue
                }
            }
            j++
        }
        if (active || (depth === 0 && lines[i].match(/^\s*\}>\s*\(\s*\)/))) {
            inMacro[i] = true
        }
    }
    return inMacro
}

function fingerprint(line) {
    return line
        .replace(/(['"`])(?:\\.|(?!\1)[^\\])*\1/g, '""')
        .replace(/\d+(\.\d+)?/g, '0')
        .replace(/\b[a-zA-Z_][a-zA-Z0-9_]{2,}\b/g, 'I')
        .replace(/\s+/g, ' ')
        .trim()
}

function isBoilerplateWindow(lines, macroFlags) {
    let trivial = 0
    for (let i = 0; i < lines.length; i++) {
        if (macroFlags && macroFlags[i]) {
            trivial++
            continue
        }
        if (BOILERPLATE_LINE.test(lines[i])) trivial++
    }
    return trivial >= Math.ceil(lines.length / 2)
}

const buckets = new Map()
const files = [
    ...walk(SRC, '.vue'),
    ...walk(SRC, '.ts'),
].filter(f => {
    if (isInsideComponents(f)) return false
    if (f.endsWith('.d.ts')) return false
    if (f.includes(`${SRC}/api/`) || f.includes(`${SRC}/i18n/`)) return false
    return true
})

for (const file of files) {
    const lines = readFileSync(file, 'utf-8').split('\n')
    const macroFlags = markMacroBodyLines(lines)
    const seen = new Set()
    for (let i = 0; i <= lines.length - WINDOW; i++) {
        const window = lines.slice(i, i + WINDOW)
        const windowMacro = macroFlags.slice(i, i + WINDOW)
        if (isBoilerplateWindow(window, windowMacro)) continue
        const fp = window.map(fingerprint).join('\n')
        if (!fp || fp.length < 40) continue
        if (seen.has(fp)) continue
        seen.add(fp)
        if (!buckets.has(fp)) buckets.set(fp, [])
        buckets.get(fp).push({file, line: i + 1})
    }
}

const reports = []
for (const [fp, locations] of buckets) {
    if (locations.length < WARN_AT) continue
    const distinctFiles = new Set(locations.map(l => l.file)).size
    if (distinctFiles < MIN_FILES) continue
    reports.push({fp, locations, distinctFiles})
}

reports.sort((a, b) => b.locations.length - a.locations.length || b.distinctFiles - a.distinctFiles)

for (const r of reports) {
    const sample = r.fp.split('\n')[0].slice(0, 80)
    const head = `Repeated ${WINDOW}-line block (${r.locations.length}x across ${r.distinctFiles} files): ${sample}…`
    const msg = `${head}\n      first occurrences:\n        - ${
        r.locations.slice(0, 4).map(l => `${rel(l.file)}:${l.line}`).join('\n        - ')
    }`
    if (r.locations.length >= ERROR_AT) {
        error(null, 0, msg, CATEGORY)
    } else {
        warn(null, 0, msg, CATEGORY)
    }
}

console.log(
    `\n\x1b[1mDuplication Check\x1b[0m`
    + ` (window=${WINDOW}, min-files=${MIN_FILES},`
    + ` warn @ ${WARN_AT} / error @ ${ERROR_AT})`,
)
reporter.print()
reporter.exit()
