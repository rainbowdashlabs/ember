/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {demoLogin} from '~/api/auth'
import {getDemoStatus} from '~/api/demo'
import {getItem, removeItem} from '~/api/storage'
import {useConsentGuard} from '~/composables/useConsentGuard'
import {useSession} from '~/composables/useSession'
import {useCluster} from '~/composables/useCluster'
import {useStations} from '~/composables/useStations'

/**
 * How long a session may sit untouched before the requirements of the active station are
 * checked again.
 */
const IDLE_LIMIT_MS = 3600000

/**
 * The pages the idle check leaves alone, because they are the gates it would send somebody to.
 *
 * <p>Every other gate here turns a navigation away without stamping the session as active, which is
 * deliberate: a stamp written ahead of a redirect would spend the idle window before the
 * requirements were ever seen. That leaves the window open, so a gate that is itself sent away
 * bounces for ever. Consent, once out of date, redirected to {@code /reconsent}, the idle check sent
 * that to the requirements, consent sent it back, and neither ever wrote the stamp: the tab spun
 * until the browser called the page unresponsive, and only a reload broke out of it, because the
 * consent flag lives no longer than the page does.
 */
const IDLE_EXEMPT = new Set(['/station/requirements', '/reconsent'])

/**
 * Signs in as somebody else because a link said so, which only a demo or a development instance
 * allows.
 *
 * <p>It exists for the recordings and the walkthroughs: a link that carries both who is watching and
 * what they are looking at needs no spoken preamble about signing in first, and it lands on the same
 * screen every time. The endpoint behind it is registered only on those instances, so the parameter
 * is inert everywhere else; the check here keeps a production instance from even asking.
 *
 * <p>The stored station and cluster go first. They belong to whoever was signed in a moment ago, and
 * the next person is often at another station, where they would be sent to a picker or an emptiness
 * instead of the page the link named.
 *
 * @param email the address to become
 */
async function switchAccount(email: string): Promise<void> {
    const status = await getDemoStatus()
    if (!status.demo && !status.dev) return
    removeItem('station_id')
    removeItem('cluster_id')
    await demoLogin(email)
    useSession().clear()
}

/**
 * Order matters here. A link that names somebody to become is honoured before anything else, because
 * every gate below it asks about the person who is signed in. The active station is resolved next, so a link arriving with
 * {@code ?station=} hands its station over before anything else can redirect and drop the
 * parameter. The idle check runs afterwards and only once a station is known - the requirements
 * page is station-scoped, and sending someone there without one lands them back at the station
 * picker. For the same reason the activity stamp is written only when the navigation is let
 * through: a stamp written ahead of a redirect would consume the idle window without the
 * requirements ever being seen.
 *
 * The administration area is closed to anyone who is not an instance administrator. The server
 * refuses every administration endpoint on its own - this only stops the panel from opening and
 * then failing on each call. It is deliberately closed rather than open when the session cannot be
 * established: a panel that cannot be shown to work is not shown.
 */
export default defineNuxtRouteMiddleware(async (to) => {
    if (!import.meta.client) return

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

    if (to.meta.public === true) return
    if (to.path === '/' || to.path === '/login' || to.path === '/2fa-verify') return
    if (to.path.startsWith('/helpcenter')) return

    const publicPaths = [
        '/forgot-password', '/set-password', '/reset-password', '/confirm-email-change', '/install',
        '/apply', '/waitlist', '/style', '/privacy', '/terms', '/imprint', '/patch-notes',
        '/discovery', '/public', '/waiting-list', '/unlock-device', '/enroll',
    ]
    if (publicPaths.some(p => to.path.startsWith(p))) return

    const token = getItem('session_token')
    if (!token) {
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

    // A cluster area belongs to whoever may act for a cluster, and nobody else has one to be shown.
    // Without this the shell opens on an emptiness that explains itself, which reads as a page somebody
    // is meant to be on.
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
            return navigateTo({path: '/cross-station', query: {redirect: to.fullPath}})
        }
    }

    if (!getItem('station_id')) return

    const lastActivity = localStorage.getItem('ember_last_activity')
    const now = Date.now()
    if (lastActivity && now - Number(lastActivity) > IDLE_LIMIT_MS && !IDLE_EXEMPT.has(to.path)) {
        return navigateTo({path: '/station/requirements', query: {redirect: to.fullPath}})
    }
    localStorage.setItem('ember_last_activity', String(now))
})
