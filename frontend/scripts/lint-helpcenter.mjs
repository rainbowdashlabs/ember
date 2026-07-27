#!/usr/bin/env node
/**
 * Help Center Coverage Linter
 *
 * Checks that every app route has a corresponding help center route.
 * Covers: station panel, admin panel, and (future) cluster panel.
 *
 * Exit code 1 if any app routes lack a help center counterpart.
 */

import {readFileSync} from 'fs'
import {join} from 'path'
import {parseRoutes, normalizePath, SRC, RED, GREEN, YELLOW, RESET, BOLD} from './lint-utils.mjs'

const allRoutes = parseRoutes()

// ── Known gaps (pre-existing, tracked as warnings) ──────────────────
//
// Routes that shipped without a help-center counterpart before the linter
// started reading the real Nuxt pages tree. New routes must NOT be added
// here — write the help page instead. Remove entries as pages get written.

const KNOWN_MISSING_HELP = new Set([
    'event-detail-date',
    'event-templates',
    'event-template-edit',
    'federated-board-view',
    'federated-ticket-detail',
    'federated-event-detail',
    'federated-news-detail',
    'inventory-item-detail',
    'inventory-my',
    'news-detail',
    'news-create',
    'quiz-catalog-mc-fill',
    'station-setup',
    'station-setup-address',
    'station-setup-branding',
    'station-setup-federation',
    'station-setup-finish',
    'station-setup-first-event',
    'station-setup-groups',
    'station-setup-invites',
    'station-setup-kb-seed',
    'station-setup-mail',
    'station-setup-member-types',
    'station-setup-modules',
    'station-setup-welcome',
    'admin-problem-reports',
])

const KNOWN_SHARED_HELP_COMPONENTS = new Set([
    '@/views/helpcenter/stationview/inventory/CheckMemberHelp',
    '@/views/helpcenter/stationview/procedure/ProcedureOverviewHelp',
])

// ── Panel definitions ───────────────────────────────────────────────

const panels = [
    {
        label: 'Station',
        appFilter: (r) => !['help-', 'admin-', 'account-', 'login', 'forgot', 'set-password', 'station-select',
            'cross-station-dashboard', '2fa-verify', 'apply', 'waitlist-', 'waiting-list', 'home', 'privacy', 'terms', 'reconsent', 'imprint',
            'patch-notes', 'reset-password', 'public-', 'not-found', 'style', 'helpcenter-', 'requirements']
            .some(p => r.name.startsWith(p)),
        helpFilter: (r) => r.name.startsWith('help-') && !r.name.startsWith('help-admin') && !r.name.startsWith('help-cluster'),
        supplementaryHelp: (r) => r.name.startsWith('help-basics') || r.name === 'help-welcome'
            || r.name.endsWith('-overview') || r.name.endsWith('-module-overview')
            || r.path.includes('/editor') || r.path.includes('/federated') || r.path.includes('/ai')
            || r.path.includes('/mail-config') || r.path.includes('/theme') || r.path.includes('/feeds/')
            || r.path === 'knowledge/browse' || r.path === 'members/waiting-lists',
    },
    {
        label: 'Admin',
        appFilter: (r) => r.name.startsWith('admin-'),
        helpFilter: (r) => r.name.startsWith('help-admin-'),
        supplementaryHelp: (r) => r.name.endsWith('-overview'),
    },
    {
        label: 'Cluster',
        appFilter: (r) => r.name.startsWith('cluster-'),
        helpFilter: (r) => r.name.startsWith('help-cluster-'),
        supplementaryHelp: (r) => r.name.endsWith('-overview'),
    },
]

// ── Run checks per panel ────────────────────────────────────────────

let totalMissing = 0

for (const panel of panels) {
    const appRoutes = allRoutes.filter(panel.appFilter).filter(r => r.path && !r.path.includes('pathMatch'))
    const helpRoutes = allRoutes.filter(panel.helpFilter).filter(r => r.path && !r.path.includes('pathMatch'))

    if (appRoutes.length === 0) continue

    const helpPathSet = new Set(helpRoutes.map(r => normalizePath(r.path)))
    const appPathSet = new Set(appRoutes.map(r => normalizePath(r.path)).filter(Boolean))

    const allMissing = appRoutes.filter(r => {
        const normalized = normalizePath(r.path)
        if (!normalized) return false
        return !helpPathSet.has(normalized)
    })
    const knownMissing = allMissing.filter(r => KNOWN_MISSING_HELP.has(r.name))
    const missing = allMissing.filter(r => !KNOWN_MISSING_HELP.has(r.name))

    const orphaned = helpRoutes.filter(r => {
        const normalized = normalizePath(r.path)
        if (!normalized) return false
        if (panel.supplementaryHelp(r)) return false
        return !appPathSet.has(normalized)
    })

    const covered = appRoutes.length - allMissing.length
    const pct = Math.round((covered / appRoutes.length) * 100)

    console.log(`\n${BOLD}${panel.label} Panel${RESET}`)
    console.log(`  App routes: ${appRoutes.length} | Help routes: ${helpRoutes.length} | Coverage: ${covered}/${appRoutes.length} (${pct}%)`)

    if (knownMissing.length > 0) {
        console.log(`\n  ${YELLOW}Known missing help pages (${knownMissing.length}) — tracked in KNOWN_MISSING_HELP:${RESET}`)
        for (const {name, path} of knownMissing.sort((a, b) => a.path.localeCompare(b.path))) {
            console.log(`    ${YELLOW}warning${RESET} ${name} → ${path}`)
        }
    }

    if (orphaned.length > 0) {
        console.log(`\n  ${YELLOW}Orphaned help pages (${orphaned.length}) — path doesn't match any app route:${RESET}`)
        for (const {name, path} of orphaned.sort((a, b) => a.path.localeCompare(b.path))) {
            console.log(`    ${YELLOW}warning${RESET} ${name} → ${path}`)
        }
    }

    if (missing.length > 0) {
        console.log(`\n  ${RED}Missing help pages (${missing.length}):${RESET}`)
        for (const {name, path} of missing.sort((a, b) => a.path.localeCompare(b.path))) {
            console.log(`    ${RED}error${RESET} ${name} → ${path}`)
        }
        totalMissing += missing.length
    }

    if (allMissing.length === 0 && orphaned.length === 0) {
        console.log(`  ${GREEN}✓ Full coverage, no orphans${RESET}`)
    }
}

// ── Duplicate component check ───────────────────────────────────────

console.log(`\n${BOLD}Duplicate Help Component Check${RESET}`)

const helpComponentUsage = new Map()
for (const r of allRoutes) {
    if (!r.name || !r.name.startsWith('help-')) continue
    if (!r.component) continue
    const comp = r.component
    if (!helpComponentUsage.has(comp)) helpComponentUsage.set(comp, [])
    helpComponentUsage.get(comp).push(r)
}

let duplicateCount = 0
for (const [comp, routes] of helpComponentUsage) {
    if (routes.length <= 1) continue
    const known = KNOWN_SHARED_HELP_COMPONENTS.has(comp)
    if (!known) duplicateCount++
    const short = comp.replace(/.*\/views\//, '')
    const color = known ? YELLOW : RED
    const label = known ? 'warning' : 'error'
    console.log(`  ${color}${label}${RESET} ${short} used ${routes.length} times:`)
    for (const r of routes) {
        console.log(`    - ${r.name} → ${r.path}`)
    }
}

if (duplicateCount === 0) {
    console.log(`  ${GREEN}✓ No duplicate help components${RESET}`)
} else {
    totalMissing += duplicateCount
}

// ── Section overview check ──────────────────────────────────────────

console.log(`\n${BOLD}Section Overview Check${RESET}`)

const missingSectionOverviews = []

for (const panel of panels) {
    const helpRoutes = allRoutes.filter(panel.helpFilter).filter(r => r.path && !r.path.includes('pathMatch'))
    if (helpRoutes.length === 0) continue

    const sections = new Map()
    for (const r of helpRoutes) {
        const seg = r.path.split('/')[0]
        if (!seg) continue
        if (!sections.has(seg)) sections.set(seg, [])
        sections.get(seg).push(r)
    }

    const SKIP_SECTIONS = ['basics', 'dashboard']

    for (const [section, routes] of sections) {
        if (SKIP_SECTIONS.includes(section)) continue
        if (routes.length < 2) continue

        const hasOverview = routes.some(r => r.name.endsWith('-module-overview'))
        if (!hasOverview) {
            missingSectionOverviews.push({panel: panel.label, section, routeCount: routes.length})
        }
    }
}

if (missingSectionOverviews.length > 0) {
    console.log(`  ${YELLOW}Sections missing an overview page (${missingSectionOverviews.length}):${RESET}`)
    for (const {panel, section, routeCount} of missingSectionOverviews) {
        console.log(`    ${YELLOW}warning${RESET} [${panel}] ${section}/ (${routeCount} routes, no overview)`)
    }
} else {
    console.log(`  ${GREEN}✓ All sections have overview pages${RESET}`)
}

// ── Sidebar linkage check ───────────────────────────────────────────

console.log(`\n${BOLD}Sidebar Linkage Check${RESET}`)

const sidebarFiles = [
    join(SRC, 'views', 'DashboardView.vue'),
    join(SRC, 'views', 'AdminView.vue'),
    join(SRC, 'views', 'HelpCenterStationView.vue'),
]

const sidebarRouteNames = new Set()
for (const file of sidebarFiles) {
    try {
        const content = readFileSync(file, 'utf-8')
        // Match name="xxx" in SidebarLink and SidebarExpandableLink
        const matches = content.matchAll(/\bname="([^"]+)"/g)
        for (const m of matches) sidebarRouteNames.add(m[1])
    } catch { /* file may not exist */ }
}

// Routes that don't need sidebar links
const SIDEBAR_SKIP = (r) =>
    r.name.startsWith('help-') || r.name.startsWith('helpcenter-')
    || r.path.includes(':id') || r.path.includes('pathMatch')
    || ['home', 'login', 'forgot-password', 'set-password', 'reset-password', 'apply',
        'privacy', 'terms', 'reconsent', 'imprint', 'patch-notes', 'not-found', 'style',
        'station-select', 'profile-settings'].includes(r.name)
    || r.name.startsWith('public-') || r.name.startsWith('waiting-list') || r.name.startsWith('waitlist-')

const unlinkedRoutes = allRoutes.filter(r => {
    if (!r.name || !r.path) return false
    if (SIDEBAR_SKIP(r)) return false
    return !sidebarRouteNames.has(r.name)
})

if (unlinkedRoutes.length > 0) {
    console.log(`  ${YELLOW}Routes not linked in any sidebar (${unlinkedRoutes.length}):${RESET}`)
    for (const {name, path} of unlinkedRoutes.sort((a, b) => a.path.localeCompare(b.path))) {
        console.log(`    ${YELLOW}warning${RESET} ${name} → ${path}`)
    }
} else {
    console.log(`  ${GREEN}✓ All routes are linked in sidebars${RESET}`)
}

// ── Exit ────────────────────────────────────────────────────────────

console.log('')

if (totalMissing > 0) {
    console.log(`${RED}${BOLD}${totalMissing} missing help center page(s).${RESET}\n`)
    process.exit(1)
} else {
    console.log(`${GREEN}${BOLD}All panels have full help center coverage.${RESET}\n`)
}
