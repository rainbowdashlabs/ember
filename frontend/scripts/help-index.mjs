/**
 * Reads the help centre out of the pages tree.
 *
 * Every help page is a page file naming a route and mounting one article component, and every article
 * draws its text from keys under `helpCenter`. Both facts are already written down in the source, so the
 * search index is read from them rather than kept beside them in a list somebody has to remember.
 *
 * What a page contributes is the keys only it uses. A key several articles share is vocabulary rather
 * than text: `helpCenter.roles.member` labels a toggle on thirty pages and belongs to none of them.
 */

import {readFileSync, existsSync} from 'fs'
import {dirname, join, relative, sep} from 'path'
import {walk, PAGES_DIR, SRC} from './lint-utils.mjs'

/**
 * From how many articles on a subtree counts as shared vocabulary rather than one article's text.
 */
const SHARED_PREFIX_USERS = 5

/**
 * Every `helpCenter.…` key a file mentions, dynamic ones included: a key built with a template string
 * still tells us which subtree the article draws from, which is all the index needs.
 */
function keysIn(source) {
    const keys = []
    for (const match of source.matchAll(/helpCenter\.([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*)/g)) {
        keys.push(match[1])
    }
    return keys
}

/** The longest dotted prefix every key shares, never shorter than the root itself. */
function commonPrefix(keys) {
    const split = keys.map(key => key.split('.'))
    const shortest = Math.min(...split.map(parts => parts.length))
    const shared = []
    for (let index = 0; index < shortest; index++) {
        const segment = split[0][index]
        if (!split.every(parts => parts[index] === segment)) break
        shared.push(segment)
    }
    // The last shared segment is a leaf when every key is that key, so keep at least the root
    if (shared.length === split[0].length && shared.length > 1) shared.pop()
    return shared.length > 0 ? shared.join('.') : split[0][0]
}

/**
 * Every help page as the router sees it: the address it answers on, the name it is reached by, and the
 * article it mounts. Read from the page files themselves, because they are what makes a route exist.
 */
function helpPageFiles() {
    return walk(join(PAGES_DIR, 'helpcenter'), '.vue')
        .map(file => {
            const source = readFileSync(file, 'utf-8')
            const meta = source.match(/definePageMeta\(\{([\s\S]*?)\}\)/)
            const name = meta && meta[1].match(/name:\s*'([^']+)'/)
            if (!name || /redirect:\s*'/.test(meta[1])) return null
            const component = source.match(/import\s+\w+\s+from\s+'[~@]\/(views\/[^']+)'/)
            let path = relative(PAGES_DIR, file).split(sep).join('/').replace(/\.vue$/, '')
            if (path.endsWith('/index')) path = path.slice(0, -'/index'.length)
            return {
                name: name[1],
                path: `/${linkable(path)}`,
                component: component ? `@/${component[1]}` : null,
            }
        })
        .filter(Boolean)
}

/**
 * An address a search result can actually be sent to.
 *
 * <p>A help page about one thing of many is written with a parameter in its file name, and a parameter
 * is not an address: a result linking to `news/[id]` leads nowhere. An optional one is dropped, because
 * the page answers without it, and a required one is given a nought, which is what the help centre's own
 * links use.
 */
function linkable(path) {
    return path
        .split('/')
        .map(segment => {
            if (segment.startsWith('[[') && segment.endsWith(']]')) return null
            if (segment.startsWith('[') && segment.endsWith(']')) return '0'
            return segment
        })
        .filter(segment => segment !== null)
        .join('/')
}

/**
 * The article a help page mounts and every help component it hands the page over to, as one text.
 *
 * <p>An article that delegates is common: the page explaining how a news entry is written mounts a
 * component that renders the editing article, because the two screens are the same screen. Reading only
 * the file the page names would leave those pages with no text and no place in the search.
 */
function articleSource(route, seen = new Set()) {
    const parts = []
    const visit = (fromFile, specifier) => {
        const file = resolveComponent(fromFile, specifier)
        if (!file || seen.has(file) || !existsSync(file)) return
        seen.add(file)
        const source = readFileSync(file, 'utf-8')
        parts.push(source)
        for (const match of source.matchAll(/import\s+\w+\s+from\s+'([^']+\.vue)'/g)) {
            visit(file, match[1])
        }
    }
    if (route.component) visit(null, route.component)
    return parts.join('\n')
}

/**
 * Every article file some page reaches, directly or through one that delegates to it. What is not in
 * here is text nobody can get to.
 */
export function mountedArticles() {
    const seen = new Set()
    for (const route of helpPageFiles()) articleSource(route, seen)
    return seen
}

/**
 * Where an import specifier points, for the two shapes a help article uses: the alias every page writes
 * and the relative path a delegating article writes. Anything outside the help centre is not followed,
 * because a shared input or container carries no article text.
 */
function resolveComponent(fromFile, specifier) {
    const aliased = specifier.match(/^[~@]\/(.+)$/)
    const file = aliased ? join(SRC, aliased[1]) : fromFile ? join(dirname(fromFile), specifier) : null
    return file && file.includes(`${sep}helpcenter${sep}`) ? file : null
}

/**
 * Every help page with the key roots it draws its text from.
 *
 * @returns {{route: string, path: string, component: string|null, prefixes: string[]}[]}
 */
export function collectHelpPages() {
    const pages = helpPageFiles().map(route => ({route, keys: keysIn(articleSource(route))}))

    const drawn = pages.map(({route, keys}) => ({route, prefixes: prefixesOf(keys)}))

    const usersPerPrefix = new Map()
    for (const {prefixes} of drawn) {
        for (const prefix of prefixes.keys()) {
            usersPerPrefix.set(prefix, (usersPerPrefix.get(prefix) ?? 0) + 1)
        }
    }

    return drawn
        .map(({route, prefixes}) => {
            const own = [...prefixes.entries()].sort((first, second) => second[1] - first[1])[0]?.[0]
            return {
                route: route.name,
                path: route.path,
                prefixes: [...prefixes.keys()]
                    .filter(prefix => prefix === own || (usersPerPrefix.get(prefix) ?? 0) < SHARED_PREFIX_USERS)
                    .sort(),
            }
        })
        .sort((first, second) => first.path.localeCompare(second.path))
}

/**
 * The subtrees a set of keys draws from, with how many keys each contributes.
 *
 * <p>Grouped by subtree rather than by root, because a root can hold several articles: the one about
 * hosting an instance and the one about its configuration file both sit under `basics`, and treating
 * the root as the article would give each of them the other's text.
 */
function prefixesOf(keys) {
    const groups = new Map()
    for (const key of keys) {
        const group = key.split('.').slice(0, 2).join('.')
        groups.set(group, [...(groups.get(group) ?? []), key])
    }
    const prefixes = new Map()
    for (const [, groupKeys] of groups) {
        const prefix = `helpCenter.${commonPrefix(groupKeys)}`
        prefixes.set(prefix, (prefixes.get(prefix) ?? 0) + groupKeys.length)
    }
    return prefixes
}
