/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {LocationQuery, RouteLocationNormalizedLoaded, RouteLocationRaw, Router} from 'vue-router'

/** Whether a path is the area at `root` or one of its pages. */
export function isInArea(path: string, root: '/station' | '/cluster'): boolean {
    return path === root || path.startsWith(`${root}/`)
}

/**
 * The query an address should carry so that it names its station or cluster, or `null` where it
 * already does or names nothing.
 *
 * <p>A page of the station area names its station as `station`, a page of the cluster area its
 * cluster as `cluster`, so an address copied, bookmarked or opened in a new tab opens the same place.
 * A query that already names one is left as it is: a link may lead to another station on purpose.
 *
 * @param path the address's path
 * @param query the address's query
 * @param stationId the station of this tab, or `null`
 * @param clusterId the cluster of this tab, or `null`
 */
export function placeQuery(
    path: string,
    query: LocationQuery,
    stationId: string | null,
    clusterId: string | null,
): LocationQuery | null {
    if (isInArea(path, '/station') && stationId && query.station === undefined) return {...query, station: stationId}
    if (isInArea(path, '/cluster') && clusterId && query.cluster === undefined) return {...query, cluster: clusterId}
    return null
}

/**
 * The same address without the station or cluster it names.
 *
 * <p>An address a picker returns to was taken while the tab was in the place the reader is leaving.
 * Followed as it is, it would take the tab straight back there instead of to the place just picked.
 *
 * @param target an address of this application, starting with a slash
 */
export function withoutPlace(target: string): string {
    const url = new URL(target, 'http://ember.invalid')
    url.searchParams.delete('station')
    url.searchParams.delete('cluster')
    return `${url.pathname}${url.search}${url.hash}`
}

/**
 * Makes every address the router resolves or goes to name the station or cluster of this tab.
 *
 * <p>Links compute their `href` through {@link Router.resolve}, so wrapping it reaches every link on
 * every page without touching one of them, also the link a reader opens in a new tab. A click on a link
 * and every navigation of the app go through {@link Router.push} or {@link Router.replace}, which are
 * wrapped the same way, so they arrive with the place already named instead of being redirected to it.
 * The getters are read on every call, so a link drawn before the tab's place was known follows once it
 * is.
 *
 * @param router the app's router
 * @param stationId the station of this tab, read when an address is resolved
 * @param clusterId the cluster of this tab, read when an address is resolved
 */
export function namePlaceInLinks(
    router: Router,
    stationId: () => string | null,
    clusterId: () => string | null,
): void {
    const resolve = router.resolve.bind(router)
    const push = router.push.bind(router)
    const replace = router.replace.bind(router)

    const placed = (to: RouteLocationRaw): RouteLocationRaw => {
        const resolved = resolve(to)
        const query = placeQuery(resolved.path, resolved.query, stationId(), clusterId())
        if (!query) return to
        const flags = typeof to === 'object' ? {replace: to.replace, force: to.force, state: to.state} : {}
        return {...flags, path: resolved.path, query, hash: resolved.hash}
    }

    router.resolve = ((to: RouteLocationRaw, currentLocation?: RouteLocationNormalizedLoaded) => {
        const resolved = resolve(to, currentLocation)
        const query = placeQuery(resolved.path, resolved.query, stationId(), clusterId())
        return query ? resolve({path: resolved.path, query, hash: resolved.hash}, currentLocation) : resolved
    }) as Router['resolve']
    router.push = (to => push(placed(to))) as Router['push']
    router.replace = (to => replace(placed(to))) as Router['replace']
}
