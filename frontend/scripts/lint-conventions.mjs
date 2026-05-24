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
 *  7. Repeated element+class patterns (>5 occurrences)
 *
 * Exit code 1 if any errors are found.
 */

import {readFileSync} from 'fs'
import {relative, sep} from 'path'
import {SRC, walk, rel, isInsideDir, isInsideComponents, extractTemplate, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const {error, warn} = reporter

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

// ── Rule 7: Code repetition ──────────────────────────────────────────

const patternCounts = new Map()

for (const file of vueFiles) {
    if (isInsideComponents(file)) continue
    const content = readFileSync(file, 'utf-8')
    const template = extractTemplate(content)
    const templateLines = template.split('\n')
    const templateStartLine = content.substring(0, content.indexOf('<template>')).split('\n').length

    for (let i = 0; i < templateLines.length; i++) {
        const line = templateLines[i].trim()
        if (line.startsWith('<!--')) continue

        const match = line.match(/^<(\w[\w-]*)(?:\s[^>]*)?\s+class="([^"]{15,})"/)
        if (!match) continue

        const [, tag, classes] = match
        const classList = classes.trim().split(/\s+/).filter(c => c.length > 0)
        if (classList.length < 3) continue

        // Skip pure layout utilities
        const layoutOnly = classList.every(c =>
            /^(flex|inline-flex|grid|gap-|items-|justify-|self-|place-|col-span|row-span|sm:|md:|lg:|xl:)/.test(c)
            || /^(space-[xy]-|order-|grow|shrink|basis-)/.test(c)
        )
        if (layoutOnly) continue

        const key = `<${tag} class="${classList.sort().join(' ')}">`

        if (!patternCounts.has(key)) patternCounts.set(key, [])
        patternCounts.get(key).push({file, line: templateStartLine + i})
    }
}

for (const [pattern, locations] of patternCounts) {
    if (locations.length > 5) {
        const first = locations[0]
        error(first.file, first.line,
            `Repeated pattern (${locations.length}x across ${new Set(locations.map(l => rel(l.file))).size} files): ${pattern} — extract to a component.`)
    }
}

// ── Output ───────────────────────────────────────────────────────────

reporter.print()
reporter.exit()
