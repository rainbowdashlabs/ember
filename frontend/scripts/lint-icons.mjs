#!/usr/bin/env node
/**
 * Icon Linter
 *
 * Checks that every icon a file names is registered in the plugin its set belongs to:
 * `fas` and `fab` in library.add() in the FontAwesome plugin, `ph` in the Phosphor plugin.
 *
 * Both sets register icon by icon so the bundler can leave out what nobody uses, which makes each
 * plugin the whole inventory of its set and this check possible at all.
 *
 * Exit code 1 if any unregistered icons are found.
 */

import {readFileSync} from 'fs'
import {SRC, walk, rel, extractTemplate, RED, GREEN, YELLOW, RESET, BOLD, createReporter} from './lint-utils.mjs'
import {join} from 'path'

const reporter = createReporter()
const PLUGIN_TS = join(SRC, 'plugins', 'fontawesome.ts')

// ── Parse registered icons from the FontAwesome Nuxt plugin ─────────

const pluginContent = readFileSync(PLUGIN_TS, 'utf-8')

// Extract every library.add(...) call so the registration list may be
// split across one-per-line statements (better for bundler tree-shaking).
const libraryAddMatches = [...pluginContent.matchAll(/library\.add\(([^)]+)\)/g)]
if (libraryAddMatches.length === 0) {
    console.error(`Could not find library.add() in ${rel(PLUGIN_TS)}`)
    process.exit(1)
}

const registeredVarNames = new Set(
    libraryAddMatches.flatMap(m => m[1].split(',').map(s => s.trim()).filter(Boolean))
)

// Convert FA variable names to icon names: faChevronDown → chevron-down
function faVarToIconName(varName) {
    // Remove 'fa' prefix, then convert camelCase to kebab-case
    const withoutPrefix = varName.replace(/^fa/, '')
    return withoutPrefix
        .replace(/([A-Z])/g, '-$1')
        .toLowerCase()
        .replace(/^-/, '')
}

// Build set of registered icon names (kebab-case)
const registeredIcons = new Map() // iconName → {prefix: 'fas'|'fab'}

// Determine prefix from import source
const solidImportMatch = pluginContent.match(/import\s*\{([^}]+)\}\s*from\s*'@fortawesome\/free-solid-svg-icons'/)
const brandsImportMatch = pluginContent.match(/import\s*\{([^}]+)\}\s*from\s*'@fortawesome\/free-brands-svg-icons'/)

const solidVars = solidImportMatch
    ? new Set(solidImportMatch[1].split(',').map(s => s.trim()).filter(Boolean))
    : new Set()
const brandsVars = brandsImportMatch
    ? new Set(brandsImportMatch[1].split(',').map(s => s.trim()).filter(Boolean))
    : new Set()

for (const varName of registeredVarNames) {
    const iconName = faVarToIconName(varName)
    const prefix = brandsVars.has(varName) ? 'fab' : 'fas'
    registeredIcons.set(`${prefix}:${iconName}`, varName)
}

// ── Parse registered icons from the Phosphor plugin ─────────────────

const PHOSPHOR_TS = join(SRC, 'plugins', 'phosphor.ts')
const phosphorContent = readFileSync(PHOSPHOR_TS, 'utf-8')

const phosphorImport = phosphorContent.match(/import\s*\{([^}]+)\}\s*from\s*'@phosphor-icons\/vue'/)
if (!phosphorImport) {
    console.error(`Could not find the icon import in ${rel(PHOSPHOR_TS)}`)
    process.exit(1)
}

// PhFireTruck → fire-truck, which is how a template names it. Two runs of capitals rather than one,
// so that PhTShirt becomes t-shirt and not tshirt: AppIcon turns the name back into the component's
// and tshirt would come back as PhTshirt, which nothing registered.
function phVarToIconName(varName) {
    return varName
        .replace(/^Ph/, '')
        .replace(/([A-Z])([A-Z][a-z])/g, '$1-$2')
        .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
        .toLowerCase()
}

for (const varName of phosphorImport[1].split(',').map(s => s.trim()).filter(Boolean)) {
    registeredIcons.set(`ph:${phVarToIconName(varName)}`, varName)
}

/** What to add where, named for the set the missing icon belongs to. */
function registrationHint(prefix, iconName) {
    const parts = iconName.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('')
    return prefix === 'ph'
        ? `Add Ph${parts} to src/plugins/phosphor.ts.`
        : `Add fa${parts} to src/plugins/fontawesome.ts.`
}

// ── Check the gear catalogue, whose names never appear as a literal pair ────

// The catalogue stores a name and `gearIconRef` builds the pair at runtime, so the scan below
// cannot see these. Left unchecked, a kind drawn with an unregistered picture shows nothing at all.
const GEAR_ICONS_TS = join(SRC, 'util', 'gearIcons.ts')
const gearContent = readFileSync(GEAR_ICONS_TS, 'utf-8')

for (const match of gearContent.matchAll(/entry\('([^']+)'/g)) {
    const stored = match[1]
    const [prefix, iconName] = stored.startsWith('ph:') ? ['ph', stored.slice(3)] : ['fas', stored]
    if (registeredIcons.has(`${prefix}:${iconName}`)) continue
    reporter.error(GEAR_ICONS_TS, lineOf(gearContent, match.index),
        `Gear icon '${stored}' is not registered. ${registrationHint(prefix, iconName)}`)
}

// ── Scan templates for icon usage ───────────────────────────────────

const vueFiles = walk(SRC, '.vue')
// Icon usage patterns: ['fas', 'icon-name'] or ['fab', 'icon-name']
const iconRegex = /\['(fas|fab|ph)',\s*'([a-z0-9-]+)'\]/g

for (const file of vueFiles) {
    const content = readFileSync(file, 'utf-8')
    const template = extractTemplate(content)
    if (!template) continue

    const templateLines = template.split('\n')
    const templateStartLine = content.substring(0, content.indexOf('<template>')).split('\n').length

    for (let i = 0; i < templateLines.length; i++) {
        const line = templateLines[i]
        let match
        iconRegex.lastIndex = 0
        while ((match = iconRegex.exec(line)) !== null) {
            const [, prefix, iconName] = match
            const key = `${prefix}:${iconName}`
            if (!registeredIcons.has(key)) {
                reporter.error(file, templateStartLine + i,
                    `Icon '${iconName}' (${prefix}) is not registered. ${registrationHint(prefix, iconName)}`)
            }
        }
    }
}

// ── Also check script sections for programmatic icon usage ──────────

for (const file of vueFiles) {
    const content = readFileSync(file, 'utf-8')
    // Check script section too (icons can be referenced in computed/methods)
    const scriptMatch = content.match(/<script[^>]*>([\s\S]*?)<\/script>/)
    if (!scriptMatch) continue

    const script = scriptMatch[1]
    const scriptStartLine = content.substring(0, content.indexOf(scriptMatch[0])).split('\n').length

    const scriptLines = script.split('\n')
    for (let i = 0; i < scriptLines.length; i++) {
        const line = scriptLines[i]
        let match
        iconRegex.lastIndex = 0
        while ((match = iconRegex.exec(line)) !== null) {
            const [, prefix, iconName] = match
            const key = `${prefix}:${iconName}`
            if (!registeredIcons.has(key)) {
                reporter.error(file, scriptStartLine + i,
                    `Icon '${iconName}' (${prefix}) is not registered. ${registrationHint(prefix, iconName)}`)
            }
        }
    }
}

// ── Output ──────────────────────────────────────────────────────────

if (reporter.errors.length === 0) {
    console.log(`\n${GREEN}${BOLD}Icon lint passed.${RESET} All ${registeredIcons.size} registered icons are valid.\n`)
} else {
    reporter.print()
    reporter.exit()
}
