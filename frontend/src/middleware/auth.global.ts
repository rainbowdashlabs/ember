/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {demoLogin} from '~/api/auth'
import {isFirstStationNeeded} from '~/api/stations'
import {FIRST_STATION_PATH} from '~/util/signInLanding'
import {getDemoStatus} from '~/api/demo'
import {hasSessionCookie} from '~/api/sessionCookie'
import {getItem, removeItem} from '~/api/storage'
import {useConsentGuard} from '~/composables/useConsentGuard'
import {useSession} from '~/composables/useSession'
import {useCluster} from '~/composables/useCluster'
import {useStations} from '~/composables/useStations'
import {forgetLandingMemory, rememberVisitedArea} from '~/util/landingMemoryState'
import {isPublicRoute} from '~/util/publicRoute'

/**
 * How long a session may sit untouched before the requirements of the active station are
 * checked again.
 */
const IDLE_LIMIT_MS = 3600000

/**
 * The pages the idle check leaves alone, because they are the gates it would send somebody to.
 *
 * <p>No gate stamps the session before it redirects, so a gate the idle check sent away would bounce
 * for ever: out-of-date consent and the requirements once sent each other back and forth until the
 * tab hung.
 */
const IDLE_EXEMPT: ReadonlySet<string> = new Set(['/station/requirements', '/reconsent'])

/**
 * Signs in as somebody else because a link said so, which only a demo or a development instance
 * allows.
 *
 * <p>It exists for recordings and walkthroughs, so a link lands on the same screen every time. The
 * stored station, cluster and last area go first, because they belong to whoever was signed in a
 * moment ago and would send the next person somewhere other than the page the link named.
 *
 * @param email the address to become
 */
async function switchAccount(email: string): Promise<void> {
    const status = await getDemoStatus()
    if (!status.demo && !status.dev) return
    removeItem('station_id')
    removeItem('cluster_id')
    forgetLandingMemory()
    await demoLogin(email)
    useSession().clear()
}

/**
 * Whether the reader administers an instance that has no station at all yet. Such an instance has
 * nothing to choose between and no station to open, so the reader is led to found the first one.
 */
async function waitsForFirstStation(): Promise<boolean> {
    const {loaded, load, isAdmin} = useSession()
    if (!loaded.value) await load()
    if (!isAdmin()) return false
    return isFirstStationNeeded().catch(() => false)
}

/**
 * Gates every navigation, in an order that matters.
 *
 * <p>An address that matches no page is let through untouched, since there is nothing to protect and
 * a login screen is a strange answer to a typo. A link naming somebody to become is honoured next,
 * because every later gate asks about the person signed in. The administration area is closed to
 * anyone who is not an instance administrator, and stays closed when the session cannot be
 * established. A cluster area is closed to anyone who may act for no cluster, whose shell would
 * otherwise open on an emptiness that reads as a page they are meant to be on. A {@code ?station=}
 * link hands its station over before anything can redirect and drop it, and the idle check runs only
 * once a station is known, since the requirements page is station-scoped. The activity stamp is
 * written only when the navigation is let through, so a redirect never spends the idle window.
 */
export default defineNuxtRouteMiddleware(async (to) => {
    if (!import.meta.client) return

    if (to.matched && to.matched.length === 0) return

    const becomes = typeof to.query.as === 'string' ? to.query.as.trim() : null
    if (becomes) {
        const query = {...to.query}
        delete query.as
        try {
            await switchAccount(becomes)
        } catch {
            return navigateTo({path: '/login', query: {redirect: to.path}})
        }
        return navigateTo({path: to.path, query, hash: to.hash, replace: true})
    }

    if (isPublicRoute(to.path, to.meta)) return

    if (!hasSessionCookie()) {
        return navigateTo({path: '/login', query: {redirect: to.fullPath}})
    }

    const {needsReconsent} = useConsentGuard()
    if (needsReconsent.value && to.path !== '/reconsent') {
        return navigateTo('/reconsent')
    }

    if (to.path === '/admin' || to.path.startsWith('/admin/')) {
        const {loaded, load, isAdmin} = useSession()
        if (!loaded.value) await load()
        if (!isAdmin()) return navigateTo('/station/dashboard/overview')
    }

    if (to.path === '/cluster' || to.path.startsWith('/cluster/')) {
        const {loaded: clustersLoaded, load: loadClusters, hasClusters} = useCluster()
        if (!clustersLoaded.value) await loadClusters()
        if (!hasClusters.value) return navigateTo('/station/dashboard/overview')
    }

    if (to.path === '/station' || to.path.startsWith('/station/')) {
        const queryStation = typeof to.query.station === 'string' ? to.query.station : null
        if (queryStation && queryStation !== getItem('station_id')) {
            useStations().setActiveStation(queryStation)
        } else if (!queryStation && !getItem('station_id')) {
            if (await waitsForFirstStation()) return navigateTo(FIRST_STATION_PATH)
            return navigateTo({path: '/cross-station', query: {redirect: to.fullPath}})
        }
    }

    if (to.path === '/cross-station' && await waitsForFirstStation()) {
        return navigateTo(FIRST_STATION_PATH)
    }

    rememberVisitedArea(to.path)

    if (!getItem('station_id')) return

    const lastActivity = localStorage.getItem('ember_last_activity')
    const now = Date.now()
    if (lastActivity && now - Number(lastActivity) > IDLE_LIMIT_MS && !IDLE_EXEMPT.has(to.path)) {
        return navigateTo({path: '/station/requirements', query: {redirect: to.fullPath}})
    }
    localStorage.setItem('ember_last_activity', String(now))
})
