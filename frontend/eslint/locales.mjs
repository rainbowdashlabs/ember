import {mkdirSync, writeFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {createJiti} from 'jiti'

const I18N = fileURLToPath(new URL('../src/i18n/', import.meta.url))
const SNAPSHOTS = fileURLToPath(new URL('../node_modules/.cache/ember-eslint/locales/', import.meta.url))

/**
 * The locale files as `@intlify/eslint-plugin-vue-i18n` reads them.
 *
 * <p>The plugin loads messages from JSON, YAML or CommonJS files only, and the locales here are
 * TypeScript modules: one per language, with the refusals imported into the German one and the
 * help centre kept in a chunk of its own that the application merges in under `helpCenter` once a
 * help page is opened. So the modules are evaluated as the application sees them after that merge,
 * and written out as one JSON file per language for the plugin's `localeDir`.
 *
 * <p>German is the language shown and the one fallen back to, and the English file is partial on
 * purpose, so a key counts as defined when either of them carries it.
 *
 * @returns the glob the plugin's `localeDir` setting takes
 */
export async function localeSnapshots() {
    const jiti = createJiti(import.meta.url, {moduleCache: false})
    const load = async name => jiti.import(`${I18N}${name}`, {default: true})
    const german = await load('de-DE.ts')
    const helpCenter = await load('de-DE.helpcenter.ts')
    const english = await load('en.ts')

    mkdirSync(SNAPSHOTS, {recursive: true})
    writeFileSync(`${SNAPSHOTS}de-DE.json`, JSON.stringify(deepMerge(german, {helpCenter})))
    writeFileSync(`${SNAPSHOTS}en.json`, JSON.stringify(english))
    return `${SNAPSHOTS}*.json`
}

/**
 * The locale modules themselves, as a glob for the plugin's `localeDir`.
 *
 * <p>The plugin's rules for locale files act only on a file it knows as a locale, and it knows one
 * by finding it in `localeDir`. The locale parser in `i18n/locale-parser.mjs` hands them the file.
 *
 * @returns the glob of the German and English modules
 */
export function localeModules() {
    return `${I18N}{de-DE,de-DE.*,en}.ts`
}

/**
 * Two message trees merged the way `mergeLocaleMessage` merges them: nested objects key by key,
 * anything else replaced by the later value.
 *
 * @param base the tree merged into
 * @param addition the tree merged in
 * @returns a new tree carrying both
 */
function deepMerge(base, addition) {
    const merged = {...base}
    for (const [key, value] of Object.entries(addition)) {
        merged[key] = isTree(value) && isTree(merged[key]) ? deepMerge(merged[key], value) : value
    }
    return merged
}

/**
 * Whether a message value is a nested tree rather than a message.
 *
 * @param value the value to look at
 * @returns true for a plain object
 */
function isTree(value) {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
}
