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

import {readFileSync} from 'fs'
import {join, resolve, dirname} from 'path'
import {SRC, walk, rel, extractTemplate, RED, GREEN, YELLOW, RESET, BOLD, createReporter, parseRoutes} from './lint-utils.mjs'

const reporter = createReporter()

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
function resolveImport(spec) {
    if (spec.startsWith('@/')) return join(SRC, spec.slice(2)) + (spec.endsWith('.vue') ? '' : '.vue')
    return null
}

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
function viewContentHasTitle(template) {
    // Find the first <ViewContent ...> opening tag and inspect its attributes
    // up to the closing `>` (which may be `/>`).
    const re = /<ViewContent\b([^>]*)>/
    const m = template.match(re)
    if (!m) return {found: false, hasTitle: false}
    const attrs = m[1]
    // Accept either `title="..."`, `:title="..."` or `v-bind:title`.
    const hasTitle = /(?:^|\s)(?:v-bind:|:)?title\s*=/.test(attrs)
    return {found: true, hasTitle}
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
for (const [name, entry] of routeFiles) {
    const content = readFileSync(entry.file, 'utf-8')
    const template = extractTemplate(content)
    if (!template) continue

    const vc = viewContentHasTitle(template)
    if (!vc.found) {
        reporter.warn(entry.file, 0,
            `Route "${name}" (${entry.path}) does not wrap its body in <ViewContent>. The header title cannot be set.`,
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
