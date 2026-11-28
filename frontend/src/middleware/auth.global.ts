/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {RouteLocationNormalized} from 'vue-router'
import {demoLogin} from '~/api/auth'
import {isFirstStationNeeded} from '~/api/stations'
import {markOpened} from '~/api/notifications'
import {FIRST_STATION_PATH} from '~/util/signInLanding'
import {getDemoStatus} from '~/api/demo'
import {hasSessionCookie} from '~/api/sessionCookie'
import {forgetTabPlaces, tabClusterId, tabStationId} from '~/util/tabPlace'
import {isInArea} from '~/util/placeInAddress'
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
 * Signs in as somebody else because a link asked for it, which only a demo or a development instance
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
    forgetTabPlaces()
    forgetLandingMemory()
    await demoLogin(email)
    useSession().clear()
}

/**
 * Marks the notification a mail or feed link names as read, then follows the link without the
 * marker. A marker naming no notification is dropped, and a failure to mark it never stops the
 * reader from reaching the page.
 *
 * @param to the navigation carrying {@code ?read=<id>}
 */
async function followReadMarker(to: RouteLocationNormalized) {
    const query = {...to.query}
    delete query.read
    const opened = Number(to.query.read)
    if (Number.isInteger(opened)) await markOpened(opened).catch(() => {})
    return navigateTo({path: to.path, query, hash: to.hash, replace: true})
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
 * The same address naming the station or cluster of this tab, in place of the one that named none,
 * so the address bar can always be copied. The navigation has not happened yet, so no history entry
 * is left behind.
 */
function withPlace(to: RouteLocationNormalized, key: 'station' | 'cluster', uid: string) {
    return navigateTo({path: to.path, query: {...to.query, [key]: uid}, hash: to.hash}, {replace: true})
}

/**
 * Loads the address anew. The session, the menus and what the screens hold were answered for the
 * place the tab was in, and a link to another station or cluster has to start over with the new one,
 * as a switch through the picker does.
 */
function reloadAt(to: RouteLocationNormalized) {
    return navigateTo(to.fullPath, {external: true})
}

/**
 * Settles the cluster of a navigation into the cluster area, or turns it away. A cluster the link
 * names, where the reader may act for it, becomes the tab's; an address naming none gets the tab's.
 *
 * @returns where to go instead, or `undefined` to let the navigation through
 */
async function placeCluster(to: RouteLocationNormalized) {
    const {loaded, load, hasClusters, clusterList, setActiveCluster} = useCluster()
    if (!loaded.value) await load()
    if (!hasClusters.value) return navigateTo('/station/dashboard/overview')
    const queryCluster = typeof to.query.cluster === 'string' ? to.query.cluster : null
    if (queryCluster && clusterList.value.some(cluster => cluster.uid === queryCluster)) {
        if (queryCluster === tabClusterId()) return undefined
        const answeredFor = useSession().sessionClusterId.value
        setActiveCluster(queryCluster)
        return answeredFor !== null && answeredFor !== queryCluster ? reloadAt(to) : undefined
    }
    const cluster = queryCluster ? null : tabClusterId()
    return cluster ? withPlace(to, 'cluster', cluster) : undefined
}

/**
 * Settles the station of a navigation into the station area. A station the link names becomes the
 * tab's before anything can redirect and drop it, where the account belongs to it; one it does not
 * belong to, as in an address copied by somebody else, is dropped. An address naming none gets the
 * tab's, or the sole station of the account, or goes to the picker or to founding the first station.
 *
 * @returns where to go instead, or `undefined` to let the navigation through
 */
async function placeStation(to: RouteLocationNormalized) {
    const queryStation = typeof to.query.station === 'string' ? to.query.station : null
    if (queryStation) {
        if (queryStation === tabStationId()) return undefined
        const stations = useStations()
        if (!stations.loaded.value) await stations.load()
        if (!stations.stationList.value.some(station => station.stationId === queryStation)) {
            const {station: _foreign, ...query} = to.query
            return navigateTo({path: to.path, query, hash: to.hash}, {replace: true})
        }
        const answeredFor = useSession().sessionStationId.value
        stations.setActiveStation(queryStation)
        return answeredFor !== null && answeredFor !== queryStation ? reloadAt(to) : undefined
    }
    let station = tabStationId()
    if (!station) {
        if (await waitsForFirstStation()) return navigateTo(FIRST_STATION_PATH)
        const stations = useStations()
        station = await stations.soleStationId()
        if (!station) return navigateTo({path: '/cross-station', query: {redirect: to.fullPath}})
        stations.setActiveStation(station)
    }
    return withPlace(to, 'station', station)
}

/**
 * Gates every navigation, in an order that matters.
 *
 * <p>An address that matches no page is let through untouched, since there is nothing to protect and
 * a login screen is a strange answer to a typo. A link naming somebody to become is honoured next,
 * because every later gate asks about the person signed in. The administration area is closed to
 * anyone who is not an instance administrator, and stays closed when the session cannot be
 * established. A cluster area is closed to anyone who may act for no cluster, whose shell would
 * otherwise open on an emptiness that reads as a page they are meant to be on, and a {@code ?cluster=}
 * link from an association's mail switches to that association where the reader may act for it. A
 * {@code ?read=} link from a mail or a feed marks its notification read and is followed without the
 * marker, once the reader is signed in, so the login redirect carries it along. Every address of
 * the station area names its station as {@code ?station=}, and every address of the cluster area its
 * cluster as {@code ?cluster=}: one that names none gets the tab's own. One that names another than
 * the tab's moves the tab there before anything can redirect and drop it, and loads the page anew
 * where the session was answered for the place the tab was in. The idle check runs only once a
 * station is known, since the requirements page is station-scoped. The activity stamp is
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

    if (to.query.read !== undefined) return followReadMarker(to)

    if (to.path === '/admin' || to.path.startsWith('/admin/')) {
        const {loaded, load, isAdmin} = useSession()
        if (!loaded.value) await load()
        if (!isAdmin()) return navigateTo('/station/dashboard/overview')
    }

    if (isInArea(to.path, '/cluster')) {
        const placed = await placeCluster(to)
        if (placed !== undefined) return placed
    }

    if (isInArea(to.path, '/station')) {
        const placed = await placeStation(to)
        if (placed !== undefined) return placed
    }

    if (to.path === '/cross-station' && await waitsForFirstStation()) {
        return navigateTo(FIRST_STATION_PATH)
    }

    rememberVisitedArea(to.path)

    if (!tabStationId()) return

    const lastActivity = localStorage.getItem('ember_last_activity')
    const now = Date.now()
    if (lastActivity && now - Number(lastActivity) > IDLE_LIMIT_MS && !IDLE_EXEMPT.has(to.path)) {
        return navigateTo({path: '/station/requirements', query: {redirect: to.fullPath}})
    }
    localStorage.setItem('ember_last_activity', String(now))
})
