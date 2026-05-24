/**
 * Shared utilities for frontend linters.
 */

import {readdirSync, readFileSync} from 'fs'
import {join, relative, sep} from 'path'

// ── Paths ───────────────────────────────────────────────────────────

export const SRC = new URL('../src', import.meta.url).pathname
export const ROUTER_FILE = join(SRC, 'router', 'index.ts')

// ── Colors ──────────────────────────────────────────────────────────

export const RED = '\x1b[31m'
export const GREEN = '\x1b[32m'
export const YELLOW = '\x1b[33m'
export const RESET = '\x1b[0m'
export const BOLD = '\x1b[1m'

// ── File helpers ────────────────────────────────────────────────────

export function walk(dir, ext) {
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

export function rel(file) {
    return relative(join(SRC, '..'), file)
}

export function isInsideDir(file, dirSegment) {
    const r = relative(SRC, file)
    return r.startsWith(`components${sep}${dirSegment}${sep}`)
}

export function isInsideComponents(file) {
    const r = relative(SRC, file)
    return r.startsWith(`components${sep}`)
}

export function extractTemplate(content) {
    const start = content.indexOf('<template>')
    const end = content.lastIndexOf('</template>')
    if (start === -1 || end === -1) return ''
    return content.substring(start, end + '</template>'.length)
}

// ── Router parsing ──────────────────────────────────────────────────

/**
 * Parse all named routes from the router file.
 * Returns array of {name, path} objects.
 */
export function parseRoutes() {
    const content = readFileSync(ROUTER_FILE, 'utf-8')
    const lines = content.split('\n')
    const routes = []

    for (let i = 0; i < lines.length; i++) {
        const pathMatch = lines[i].match(/^\s*path:\s*'([^']*)'/)
        if (!pathMatch) continue

        for (let j = i + 1; j < Math.min(i + 6, lines.length); j++) {
            const nameMatch = lines[j].match(/^\s*name:\s*'([^']*)'/)
            if (nameMatch) {
                routes.push({name: nameMatch[1], path: pathMatch[1]})
                break
            }
            if (lines[j].match(/^\s*path:\s*'/) || lines[j].match(/^\s{8,12}}/)) break
        }
    }

    return routes
}

/**
 * Normalize a route path for comparison.
 * Replaces all :paramName and :paramName? with :id.
 */
export function normalizePath(path) {
    return path
        .replace(/:[a-zA-Z]+\??/g, ':id')
        .replace(/^\/|\/$/g, '')
}

// ── Reporting ───────────────────────────────────────────────────────

/**
 * Creates an error/warning collector with formatted output.
 */
export function createReporter() {
    const errors = []
    const warnings = []

    return {
        error(file, line, msg, category = '') {
            errors.push({file: typeof file === 'string' && file.startsWith('/') ? rel(file) : file, line, msg, category})
        },
        warn(file, line, msg, category = '') {
            warnings.push({file: typeof file === 'string' && file.startsWith('/') ? rel(file) : file, line, msg, category})
        },
        get errors() { return errors },
        get warnings() { return warnings },

        print() {
            const printGroup = (items, color, label) => {
                if (items.length === 0) return
                console.log(`\n${color}${BOLD}${label} (${items.length}):${RESET}`)
                // Group by category
                const groups = new Map()
                for (const item of items) {
                    const cat = item.category || 'Other'
                    if (!groups.has(cat)) groups.set(cat, [])
                    groups.get(cat).push(item)
                }
                for (const [cat, entries] of groups) {
                    if (groups.size > 1) console.log(`\n  ${BOLD}${cat}${RESET}`)
                    for (const e of entries) {
                        if (e.file) {
                            const loc = e.line > 0 ? `:${e.line}` : ''
                            console.log(`    ${color}${label.toLowerCase().slice(0, -1)}${RESET} ${e.file}${loc}: ${e.msg}`)
                        } else {
                            console.log(`    ${color}${label.toLowerCase().slice(0, -1)}${RESET} ${e.msg}`)
                        }
                    }
                }
            }
            printGroup(warnings, YELLOW, 'Warnings')
            printGroup(errors, RED, 'Errors')
        },

        exit() {
            if (errors.length > 0) {
                console.log(`\n${RED}Lint failed with ${errors.length} error(s).${RESET}\n`)
                process.exit(1)
            } else if (warnings.length > 0) {
                console.log(`\n${YELLOW}Lint passed with ${warnings.length} warning(s).${RESET}\n`)
            } else {
                console.log(`\nLint passed.\n`)
            }
        }
    }
}
