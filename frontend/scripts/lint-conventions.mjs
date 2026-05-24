#!/usr/bin/env node
/**
 * Convention linter for the Ember frontend.
 *
 * Checks:
 *  1. No raw <button> outside src/components/button/
 *  2. No raw <input>/<select>/<textarea> outside src/components/input/
 *  3. No raw <h1>/<h2>/<h3> outside src/components/typography/
 *  4. No more than 6 CSS class arguments per element outside src/components/
 *  5. .vue files in src/views/ must not exceed 500 lines (error)
 *  6. .vue files > 300 lines get a warning
 *
 * Exit code 1 if any errors are found.
 */

import {readdirSync, readFileSync} from 'fs'
import {join, relative, sep} from 'path'

const SRC = new URL('../src', import.meta.url).pathname

// ── Helpers ──────────────────────────────────────────────────────────

function walk(dir, ext) {
    const results = []
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) {
            results.push(...walk(full, ext))
        } else if (entry.name.endsWith(ext)) {
            results.push(full)
        }
    }
    return results
}

function rel(file) {
    return relative(join(SRC, '..'), file)
}

function isInsideDir(file, dirSegment) {
    const r = relative(SRC, file)
    return r.startsWith(`components${sep}${dirSegment}${sep}`)
}

function isInsideComponents(file) {
    const r = relative(SRC, file)
    return r.startsWith(`components${sep}`)
}

// Extract only the <template> section from a .vue file
function extractTemplate(content) {
    const start = content.indexOf('<template>')
    const end = content.lastIndexOf('</template>')
    if (start === -1 || end === -1) return ''
    return content.substring(start, end + '</template>'.length)
}

// ── Rules ────────────────────────────────────────────────────────────

const errors = []
const warnings = []

function error(file, line, msg) {
    errors.push({file: rel(file), line, msg})
}

function warn(file, line, msg) {
    warnings.push({file: rel(file), line, msg})
}

const vueFiles = walk(SRC, '.vue')

for (const file of vueFiles) {
    const content = readFileSync(file, 'utf-8')
    const lines = content.split('\n')
    const template = extractTemplate(content)
    const templateLines = template.split('\n')
    const templateStartLine = content.substring(0, content.indexOf('<template>')).split('\n').length

    // ── Rule 1: No raw <button> outside components/button/ and components/input/ ──
    if (!isInsideDir(file, 'button') && !isInsideDir(file, 'input')) {
        for (let i = 0; i < templateLines.length; i++) {
            const line = templateLines[i]
            // Match <button but not <!-- or comments
            if (/<button[\s>]/i.test(line) && !line.trim().startsWith('<!--')) {
                error(file, templateStartLine + i, `Raw <button> usage. Use a styled button component (PrimaryButton, SecondaryButton, IconButton, etc.)`)
            }
        }
    }

    // ── Rule 2: No raw <input>/<select>/<textarea> outside components/input/ ──
    if (!isInsideDir(file, 'input')) {
        for (let i = 0; i < templateLines.length; i++) {
            const line = templateLines[i]
            if (line.trim().startsWith('<!--')) continue
            // Allow <input type="file"> since there's no component wrapper for file inputs
            if (/<input[\s]/i.test(line) && !/type\s*=\s*["']file["']/i.test(line)) {
                error(file, templateStartLine + i, `Raw <input> usage. Use TextInput, NumberInput, DateInput, etc.`)
            }
            if (/<select[\s>]/i.test(line)) {
                error(file, templateStartLine + i, `Raw <select> usage. Use SelectInput.`)
            }
            if (/<textarea[\s>]/i.test(line)) {
                error(file, templateStartLine + i, `Raw <textarea> usage. Use TextAreaInput.`)
            }
        }
    }

    // ── Rule 3: No raw <h1>/<h2>/<h3> outside components/typography/ ──
    if (!isInsideDir(file, 'typography')) {
        for (let i = 0; i < templateLines.length; i++) {
            const line = templateLines[i]
            if (line.trim().startsWith('<!--')) continue
            if (/<h[123][\s>]/i.test(line)) {
                error(file, templateStartLine + i, `Raw <${line.match(/<(h[123])/i)?.[1]}> usage. Consider using PageHeader, SectionHeader, or SubHeader.`)
            }
        }
    }

    // ── Rule 4: No more than 6 class arguments per element outside components/ ──
    if (!isInsideComponents(file)) {
        for (let i = 0; i < templateLines.length; i++) {
            const line = templateLines[i]
            // Find class="..." attributes and count space-separated classes
            const classMatches = line.matchAll(/\bclass="([^"]*)"/g)
            for (const match of classMatches) {
                const classes = match[1].trim().split(/\s+/).filter(c => c.length > 0)
                if (classes.length > 6) {
                    error(file, templateStartLine + i, `Element has ${classes.length} CSS classes (max 6). Extract it to a component.`)
                } else if (classes.length > 4) {
                    warn(file, templateStartLine + i, `Element has ${classes.length} CSS classes (max 4). Consider extracting to a component.`)
                }
            }
        }
    }

    // ── Rule 5 & 6: File size limits ──
    const isView = relative(SRC, file).startsWith(`views${sep}`)
    const lineCount = lines.length

    if (isView && lineCount > 500) {
        error(file, 0, `View has ${lineCount} lines (max 500). Split into smaller components.`)
    } else if (lineCount > 300) {
        warn(file, 0, `Component has ${lineCount} lines. Consider splitting.`)
    }
}

// ── Rule 7: Code repetition — element+class patterns used more than 5 times ──
// Detects patterns like <label class="block text-sm font-medium"> appearing in many files.
// Only checks non-component files (views, etc.)

const patternCounts = new Map() // pattern -> [{file, line}]

for (const file of vueFiles) {
    if (isInsideComponents(file)) continue
    const content = readFileSync(file, 'utf-8')
    const template = extractTemplate(content)
    const templateLines = template.split('\n')
    const templateStartLine = content.substring(0, content.indexOf('<template>')).split('\n').length

    for (let i = 0; i < templateLines.length; i++) {
        const line = templateLines[i].trim()
        if (line.startsWith('<!--')) continue

        // Match <element class="classes"> patterns (at least 3 classes)
        const match = line.match(/^<(\w[\w-]*)(?:\s[^>]*)?\s+class="([^"]{15,})"/)
        if (!match) continue

        const [, tag, classes] = match
        const classList = classes.trim().split(/\s+/).filter(c => c.length > 0)
        if (classList.length < 3) continue

        // Skip pure layout utilities (flex/grid containers with only positioning classes)
        const layoutOnly = classList.every(c =>
            /^(flex|inline-flex|grid|gap-|items-|justify-|self-|place-|col-span|row-span|sm:|md:|lg:|xl:)/.test(c)
            || /^(space-[xy]-|order-|grow|shrink|basis-)/.test(c)
        )
        if (layoutOnly) continue

        // Normalize: sort classes alphabetically for consistent matching
        const key = `<${tag} class="${classList.sort().join(' ')}">`

        if (!patternCounts.has(key)) patternCounts.set(key, [])
        patternCounts.get(key).push({file, line: templateStartLine + i})
    }
}

for (const [pattern, locations] of patternCounts) {
    if (locations.length > 5) {
        // Report once with the count and first occurrence
        const first = locations[0]
        error(first.file, first.line,
            `Repeated pattern (${locations.length}x across ${new Set(locations.map(l => rel(l.file))).size} files): ${pattern} — extract to a component.`)
    }
}

// ── Output ───────────────────────────────────────────────────────────

const RED = '\x1b[31m'
const YELLOW = '\x1b[33m'
const RESET = '\x1b[0m'
const BOLD = '\x1b[1m'

if (warnings.length > 0) {
    console.log(`\n${YELLOW}${BOLD}Warnings (${warnings.length}):${RESET}`)
    for (const w of warnings) {
        const loc = w.line > 0 ? `:${w.line}` : ''
        console.log(`  ${YELLOW}warning${RESET} ${w.file}${loc}: ${w.msg}`)
    }
}

const strict = process.argv.includes('--strict')

if (errors.length > 0) {
    console.log(`\n${RED}${BOLD}Errors (${errors.length}):${RESET}`)
    for (const e of errors) {
        const loc = e.line > 0 ? `:${e.line}` : ''
        console.log(`  ${RED}error${RESET} ${e.file}${loc}: ${e.msg}`)
    }
    if (strict) {
        console.log(`\n${RED}Convention lint failed with ${errors.length} error(s).${RESET}\n`)
        process.exit(1)
    } else {
        console.log(`\n${YELLOW}Convention lint found ${errors.length} error(s) (not blocking build — use --strict to enforce).${RESET}\n`)
    }
} else if (warnings.length > 0) {
    console.log(`\n${YELLOW}Convention lint passed with ${warnings.length} warning(s).${RESET}\n`)
} else {
    console.log(`\nConvention lint passed.\n`)
}
