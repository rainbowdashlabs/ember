/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment node */
import {readdirSync, statSync} from 'node:fs'
import {join, relative} from 'node:path'
import {describe, expect, it} from 'vitest'
import {ACCOUNT_NAV} from '@/data/sidebar/accountNav'
import {ADMIN_NAV, type AdminNavContext} from '@/data/sidebar/adminNav'
import {CLUSTER_NAV, type ClusterNavContext} from '@/data/sidebar/clusterNav'
import {MOVED_STATION_NAV, STATION_NAV, type StationNavContext} from '@/data/sidebar/stationNav'
import type {NavEntryDef, NavNode} from '@/data/sidebar/navTypes'
import type {MonitoringCounts, SidebarCounts} from '@/api/generated/schema'
import {navPages, resolveNav} from '@/util/navTree'

/**
 * What every sidebar declared as data promises about its addresses: each page it lists, and each address a
 * section declares beyond its pages, is one a page file answers at.
 *
 * <p>The lists are resolved for a reader who holds everything, so no entry hides behind a permission.
 */
const PAGES_DIR = join(import.meta.dirname, '..', '..', 'pages')

function filesUnder(dir: string): string[] {
    return readdirSync(dir).flatMap(entry => {
        const path = join(dir, entry)
        return statSync(path).isDirectory() ? filesUnder(path) : [path]
    })
}

/** Every address a page file answers at, with a dynamic segment kept as written. */
function routePaths(): string[] {
    return filesUnder(PAGES_DIR)
        .filter(file => file.endsWith('.vue'))
        .map(file => `/${relative(PAGES_DIR, file).replace(/\\/g, '/').replace(/\.vue$/, '').replace(/\/?index$/, '')}`)
}

function answers(path: string, route: string): boolean {
    const want = path.split('/')
    const have = route.split('/')
    return want.length === have.length && have.every((segment, i) => segment.startsWith('[') || segment === want[i])
}

const everything = new Proxy({}, {get: (_, name) => name === 'sessionInfo' ? {value: {setupCompletedAt: null}} : () => true})

const counts = new Proxy({}, {get: () => 1})

const station: StationNavContext = {
    session: everything as StationNavContext['session'],
    counts: counts as SidebarCounts,
    boards: [],
    boardBookmarks: [],
    setupSteps: [],
}
const admin: AdminNavContext = {counts: {...counts, beaconActive: true} as unknown as MonitoringCounts, dev: true}
const cluster: ClusterNavContext = {hasClusterPermission: () => true, unreadNotifications: 0}

function resolveAll<C>(defs: NavEntryDef<C>[], context: C): NavNode[] {
    return resolveNav(defs, context, key => key)
}

const sidebars: Record<string, NavNode[]> = {
    station: resolveAll(STATION_NAV, station),
    movedStation: resolveAll(MOVED_STATION_NAV, station),
    admin: resolveAll(ADMIN_NAV, admin),
    cluster: resolveAll(CLUSTER_NAV, cluster),
    account: resolveAll(ACCOUNT_NAV, null),
}

function groupsOf(nodes: NavNode[]): Extract<NavNode, {kind: 'group'}>[] {
    return nodes.flatMap(node => node.kind === 'group' ? [node, ...groupsOf(node.children)] : [])
}

describe.each(Object.entries(sidebars))('the %s sidebar', (_, nodes) => {
    const routes = routePaths()

    it('lists only pages that exist', () => {
        const missing = navPages(nodes).map(page => page.to).filter(path => !routes.some(route => answers(path, route)))
        expect(missing).toEqual([])
    })

    it('covers only addresses some page stands on', () => {
        const invented = groupsOf(nodes)
            .flatMap(group => group.prefix)
            .filter(prefix => !routes.some(route => answers(prefix, route) || route.startsWith(`${prefix}/`)))
        expect(invented).toEqual([])
    })
})
