#!/usr/bin/env node
/**
 * Page-title linter.
 *
 * Every view rendered under a named route must:
 *   1. Wrap its body in <ViewContent title="..."> (title prop is required).
 *   2. Not render its own top-of-page <PageHeader> or <SectionHeader>.
 *      Section headers used to introduce sub-sections deeper in the template
 *      are fine; only the very first content element is checked.
 *
 * The header title is provided by usePageHeader() which ViewContent writes
 * to via its `title` prop. The outer layout components (AdminView,
 * DashboardView, HelpCenter*, AccountView) read the shared state and forward
 * it to their sidebar chrome.
 *
 * Exit code 1 if any violation is found.
 */

import {existsSync, readFileSync} from 'fs'
import {join, resolve, dirname} from 'path'
import {SRC, PAGES_DIR, walk, rel, extractTemplate, RED, GREEN, YELLOW, RESET, BOLD, createReporter, parseRoutes} from './lint-utils.mjs'

const reporter = createReporter()

/** Layout a page falls back to when `definePageMeta` declares none. */
const DEFAULT_LAYOUT = 'default'

/**
 * Layouts that mount page-header chrome, so a ViewContent title is actually
 * rendered somewhere. The first five read usePageHeader() and forward it to
 * their AppHeader. `public-station` mounts the same AppHeader through
 * SidebarLayout but does not forward the shared header state yet — the chrome
 * exists, so the rule still applies to the pages it hosts.
 *
 * Every other layout (notably `default`, the landing-header-plus-footer shell
 * used by the public, legal and auth pages) renders no header title at all.
 */
const CHROME_LAYOUTS = new Set([
    'station',
    'admin',
    'account',
    'helpcenter',
    'helpcenter-admin',
    'public-station',
])

// Layout-root views that host their own SidebarLayout. Excluded from the
// per-route audit because they don't render their own ViewContent — child
// route views do.
const LAYOUT_ROOTS = new Set([
    'AdminView.vue',
    'DashboardView.vue',
    'AccountView.vue',
    'HelpCenterAdminView.vue',
    'HelpCenterStationView.vue',
])

// Wrapper components that never carry meaningful page content by themselves.
// The linter walks past them when hunting for a top-of-page title.
const WRAPPERS = new Set([
    'ViewContent',
    'Spinner',
    'Alert',
    'Modal',
    'ConfirmDeleteModal',
    'ConfirmActionModal',
    'Teleport',
    'Transition',
    'TransitionGroup',
    'KeepAlive',
    'NuxtPage',
    'RouterView',
    'router-view',
    'template',
    'slot',
])

// ── Resolve @/… imports so we can follow router `import('...')` paths ─
/**
 * Resolve an import specifier to an absolute `.vue` path.
 * Handles the `@/` and `~/` src aliases plus relative specifiers when the
 * importing file is known. Returns null for bare package specifiers.
 */
function resolveImport(spec, fromFile = null) {
    const withExt = s => (s.endsWith('.vue') ? s : `${s}.vue`)
    if (spec.startsWith('@/') || spec.startsWith('~/')) return withExt(join(SRC, spec.slice(2)))
    if ((spec.startsWith('./') || spec.startsWith('../')) && fromFile) {
        return withExt(resolve(dirname(fromFile), spec))
    }
    return null
}

/** Maximum number of delegation hops followed when hunting for a ViewContent. */
const MAX_DELEGATION_DEPTH = 6

/** HTML elements that need no closing tag, so they never open a nesting level. */
const VOID_HTML = new Set([
    'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
    'link', 'meta', 'param', 'source', 'track', 'wbr',
])

// ── Extract the sequence of opening tags in the template, in source order.
function openingTags(template) {
    const tags = []
    const re = /<\s*([A-Za-z][A-Za-z0-9-]*)\b/g
    let m
    while ((m = re.exec(template)) !== null) {
        tags.push(m[1])
    }
    return tags
}

// A tag counts as a "real" content element if it's not in the wrapper set
// and it starts with an uppercase letter (Vue component) or is an HTML tag
// that carries page content (p, span, h1-h6, ul, table).
function isRealContent(tag) {
    if (WRAPPERS.has(tag)) return false
    // Skip pure HTML layout tags — they're structural, not content.
    const STRUCTURAL_HTML = new Set(['div', 'section', 'main', 'span', 'br', 'hr'])
    if (STRUCTURAL_HTML.has(tag)) return false
    return true
}

// ── Detect: does the first non-wrapper content element inside the template
//    match PageHeader or SectionHeader?
function hasTopOfPageTitle(template) {
    for (const tag of openingTags(template)) {
        if (WRAPPERS.has(tag)) continue
        if (tag === 'div' || tag === 'section' || tag === 'main') continue
        if (tag === 'PageHeader' || tag === 'SectionHeader') return true
        return false
    }
    return false
}

// ── Detect: does the first (or any) <ViewContent> carry a `title` prop?
/**
 * Inspect the first `<ViewContent …>` opening tag of a template.
 * `expression` holds the bound title expression for `:title="…"`, and is null
 * for a static `title="…"` (or when no ViewContent is present).
 */
function viewContentHasTitle(template) {
    const m = template.match(/<ViewContent\b((?:"[^"]*"|'[^']*'|[^>"'])*)>/)
    if (!m) return {found: false, hasTitle: false, expression: null}
    const attrs = m[1]
    // Accept either `title="..."`, `:title="..."` or `v-bind:title`.
    const bound = attrs.match(/(?:^|\s)(?:v-bind:|:)title\s*=\s*"([^"]*)"/)
    const hasTitle = Boolean(bound) || /(?:^|\s)(?:v-bind:|:)?title\s*=/.test(attrs)
    return {found: true, hasTitle, expression: bound ? bound[1] : null}
}

/** Convert a kebab-cased template attribute name to its camelCased prop name. */
function camelize(name) {
    return name.replace(/-(\w)/g, (_, c) => c.toUpperCase())
}

/**
 * Map every default-imported component name of an SFC to its absolute path.
 * Specifiers that do not resolve to an existing `.vue` file (api modules,
 * composables, packages) are dropped.
 */
function componentImports(content, file) {
    const map = new Map()
    for (const m of content.matchAll(/import\s+(?:type\s+)?([A-Za-z][\w$]*)\s+from\s+'([^']+)'/g)) {
        const resolved = resolveImport(m[2], file)
        if (resolved && existsSync(resolved)) map.set(m[1], resolved)
    }
    return map
}

let componentIndex = null

/**
 * Fall back to a project-wide lookup by file name for Nuxt auto-imported
 * components. Ambiguous names (same base name in several directories) resolve
 * to null so the linter never follows a guess.
 */
function componentByName(tag) {
    if (componentIndex === null) {
        componentIndex = new Map()
        for (const f of allVueFiles) {
            const base = f.split('/').pop().replace(/\.vue$/, '')
            componentIndex.set(base, componentIndex.has(base) ? null : f)
        }
    }
    return componentIndex.get(tag) ?? null
}

/**
 * List the elements that sit at the top level of a template, in source order,
 * together with their raw attribute text.
 */
function topLevelElements(template) {
    const inner = template
        .replace(/^\s*<template>/, '')
        .replace(/<\/template>\s*$/, '')
        .replace(/<!--[\s\S]*?-->/g, '')
    const elements = []
    const re = /<\s*(\/?)\s*([A-Za-z][\w.-]*)((?:"[^"]*"|'[^']*'|[^>"'/])*)(\/?)\s*>/g
    let depth = 0
    let m
    while ((m = re.exec(inner)) !== null) {
        const [, closing, tag, attrs, selfClosing] = m
        if (closing) {
            depth = Math.max(0, depth - 1)
            continue
        }
        if (depth === 0) elements.push({tag, attrs})
        if (!selfClosing && !VOID_HTML.has(tag)) depth += 1
    }
    return elements
}

/**
 * Collect the prop names a parent passes on a child tag. `spread` is set when
 * the parent uses an object `v-bind`, in which case the passed set is unknown
 * and every prop is assumed to be supplied.
 */
function passedProps(attrs) {
    const names = new Set()
    let spread = false
    for (const m of attrs.matchAll(/(?:^|\s)([^\s=/>]+)(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+))?/g)) {
        let name = m[1]
        if (name === 'v-bind') {
            spread = true
            continue
        }
        if (name.startsWith('@') || name.startsWith('#')) continue
        if (name.startsWith('v-bind:')) name = name.slice('v-bind:'.length)
        else if (name.startsWith(':')) name = name.slice(1)
        else if (name.startsWith('v-')) continue
        names.add(camelize(name))
    }
    return {names, spread}
}

/** Collect the prop names an SFC declares via `defineProps<{…}>()`. */
function declaredProps(content) {
    const names = new Set()
    const generic = content.match(/defineProps<\{([\s\S]*?)\}>\(\)/)
    if (!generic) return names
    const body = generic[1].replace(/\/\*[\s\S]*?\*\//g, '')
    for (const m of body.matchAll(/(?:^|\n)\s*([A-Za-z_$][\w$]*)\s*\??\s*:/g)) names.add(m[1])
    return names
}

/** Identifiers referenced by a template expression, ignoring member access. */
function expressionIdentifiers(expression) {
    const names = new Set()
    for (const m of expression.matchAll(/(?:^|[^.\w$'"])([A-Za-z_$][\w$]*)/g)) names.add(m[1])
    return names
}

/**
 * Map every named route to the layout its page file declares. Pages that
 * declare no layout fall back to {@link DEFAULT_LAYOUT}, which is what Nuxt
 * does and what the public verification pages rely on.
 */
function pageLayouts() {
    const layouts = new Map()
    for (const file of walk(PAGES_DIR, '.vue')) {
        const meta = readFileSync(file, 'utf-8').match(/definePageMeta\(\{([\s\S]*?)\}\)/)
        if (!meta) continue
        const name = meta[1].match(/name:\s*'([^']+)'/)
        if (!name) continue
        const layout = meta[1].match(/layout:\s*'([^']+)'/)
        layouts.set(name[1], layout ? layout[1] : DEFAULT_LAYOUT)
    }
    return layouts
}

/**
 * A redirect stub renders no page of its own: its template holds nothing but
 * structural elements and loading wrappers, and its script sends the router
 * somewhere else. There is no page to title, and a ViewContent would flash a
 * header for the single tick before the redirect lands.
 */
function isRedirectStub(content, template) {
    if (!/router\s*\.\s*replace\s*\(/.test(content)) return false
    const body = template
        .replace(/^\s*<template>/, '')
        .replace(/<\/template>\s*$/, '')
        .replace(/<!--[\s\S]*?-->/g, '')
    if (/\S/.test(body.replace(/<[^>]*>/g, ''))) return false
    return openingTags(template).every(tag => !isRealContent(tag))
}

/**
 * Resolve whether a view ultimately renders a titled `<ViewContent>`, following
 * single-root delegation into project components.
 *
 * A component whose ViewContent binds its title to one of its own props only
 * counts as titled when the caller actually passes that prop down; a title
 * built from anything else (an i18n call, a literal, local state) counts as
 * titled on its own.
 *
 * Returns `{found}` when no ViewContent exists anywhere in the chain,
 * otherwise `{found, titled, file, delegate, missing}`.
 */
function resolveViewContent(file, passed, depth, seen) {
    if (depth > MAX_DELEGATION_DEPTH || seen.has(file) || !existsSync(file)) return {found: false}
    seen.add(file)

    const content = readFileSync(file, 'utf-8')
    const template = extractTemplate(content)
    if (!template) return {found: false}

    const vc = viewContentHasTitle(template)
    if (vc.found) {
        if (!vc.hasTitle) return {found: true, titled: false, file, missing: []}
        if (!vc.expression) return {found: true, titled: true, file, missing: []}
        const own = declaredProps(content)
        const missing = [...expressionIdentifiers(vc.expression)]
            .filter(id => own.has(id) && !passed.names.has(id))
        const titled = passed.spread || missing.length === 0
        return {found: true, titled, file, missing}
    }

    for (const element of topLevelElements(template)) {
        if (WRAPPERS.has(element.tag) || !/^[A-Z]/.test(element.tag)) continue
        const target = componentImports(content, file).get(element.tag) ?? componentByName(element.tag)
        if (!target) continue
        const result = resolveViewContent(target, passedProps(element.attrs), depth + 1, seen)
        if (result.found) return {...result, delegate: element.tag}
    }
    return {found: false}
}

// ── Collect all named routes → view file mapping ────────────────────
const routes = parseRoutes().filter(r => r.component)

// Map component import spec → absolute path
const routeFiles = new Map()
for (const r of routes) {
    const resolved = resolveImport(r.component)
    if (!resolved) continue
    routeFiles.set(r.name, {file: resolved, path: r.path, name: r.name})
}

// ── Also audit every .vue view under views/ that uses <ViewContent> even
//    if it is not directly bound to a named route (some are wrappers).
const allVueFiles = walk(SRC, '.vue')

// Per-file audit ─────────────────────────────────────────────────────
for (const file of allVueFiles) {
    const basename = file.split('/').pop()
    if (LAYOUT_ROOTS.has(basename)) continue
    const content = readFileSync(file, 'utf-8')
    const template = extractTemplate(content)
    if (!template) continue

    const vc = viewContentHasTitle(template)

    // The rules only apply to files that host their own <ViewContent> — those
    // are the leaf page-level views. Sub-components (modals, tiles, help-page
    // dummies, style-showcase panels) that render inside a parent ViewContent
    // are legitimately allowed to open with their own SectionHeader.
    if (!vc.found) continue

    // Rule 1: title prop is required on ViewContent.
    if (!vc.hasTitle) {
        reporter.error(file, 0,
            'ViewContent is missing the required `title` prop. Add `:title="..."` (e.g. from an i18n key).',
            'Missing title prop')
    }

    // Rule 2: no top-of-page PageHeader/SectionHeader.
    if (hasTopOfPageTitle(template)) {
        reporter.error(file, 0,
            'View renders its own top-of-page title (PageHeader / SectionHeader). Remove it — the header is set via ViewContent title prop.',
            'Top-of-page title')
    }
}

// ── Route-level audit: every named route must resolve to a component that
//    actually wraps in ViewContent (so the header title flows through).
const layouts = pageLayouts()

for (const [name, entry] of routeFiles) {
    if (!existsSync(entry.file)) continue
    if (!CHROME_LAYOUTS.has(layouts.get(name) ?? DEFAULT_LAYOUT)) continue

    const content = readFileSync(entry.file, 'utf-8')
    const template = extractTemplate(content)
    if (!template) continue
    if (isRedirectStub(content, template)) continue

    const result = resolveViewContent(entry.file, {names: new Set(), spread: false}, 0, new Set())
    if (!result.found) {
        reporter.warn(entry.file, 0,
            `Route "${name}" (${entry.path}) does not wrap its body in <ViewContent>. The header title cannot be set.`,
            'Route without ViewContent')
        continue
    }
    if (!result.titled && result.delegate) {
        reporter.warn(entry.file, 0,
            `Route "${name}" (${entry.path}) delegates to <${result.delegate}>, whose <ViewContent> title comes from `
            + `the prop(s) ${result.missing.map(p => `\`${p}\``).join(', ')} that this view does not pass. `
            + 'The header title cannot be set.',
            'Route without ViewContent')
    }
}

// ── Output ────────────────────────────────────────────────────────────
if (reporter.errors.length === 0 && reporter.warnings.length === 0) {
    console.log(`\n${GREEN}${BOLD}Page-title lint passed.${RESET}\n`)
} else {
    reporter.print()
    reporter.exit()
}
