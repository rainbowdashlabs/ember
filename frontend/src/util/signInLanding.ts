/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {clusters, session} from '@/api'
import type {Cluster} from '@/api/clusters'
import type {StationMembership} from '@/api/session'
import type {SessionInfo} from '@/api/types'

/**
 * Where a fresh session belongs, and what it makes current.
 *
 * @param path       where to send the reader
 * @param stationId  the station to make current, where exactly one is theirs
 * @param clusterUid the association to make current, where exactly one is theirs
 */
export interface SignInLanding {
    path: string
    stationId?: string
    clusterUid?: string
}

/**
 * What an account may act for, which is what every landing decision is made from.
 *
 * <p>Fetched in one round so a decision that first weighs something else, such as the area the reader
 * was last in, does not pay for the same three answers twice.
 */
export interface LandingMemberships {
    stations: StationMembership[]
    info: SessionInfo | null
    clusters: Cluster[]
}

export async function loadLandingMemberships(): Promise<LandingMemberships> {
    const [stations, info, myClusters] = await Promise.all([
        session.getStations(),
        session.getSessionInfo().catch(() => null),
        clusters.listMine().catch(() => []),
    ])
    return {stations, info, clusters: myClusters}
}

/**
 * Works out where somebody belongs from what they may act for.
 *
 * @param memberships what the account may act for
 * @param redirect    where the reader was headed before they were asked to sign in
 */
export function landingFromMemberships(memberships: LandingMemberships, redirect?: string): SignInLanding {
    const {stations, info} = memberships
    const [onlyStation] = stations
    if (stations.length === 1 && onlyStation) {
        return {path: redirect || '/station/requirements', stationId: onlyStation.stationId}
    }
    if (stations.length > 1) {
        return {path: redirect || '/cross-station'}
    }
    if (info?.instanceUserType === 'ADMINISTRATOR') {
        return {path: redirect || '/admin/dashboard/overview'}
    }
    // Somebody who runs an association and belongs to no station has it as their whole reason to be here
    const [onlyCluster] = memberships.clusters
    if (memberships.clusters.length > 0) {
        return {
            path: redirect || '/cluster',
            clusterUid: memberships.clusters.length === 1 && onlyCluster ? onlyCluster.uid : undefined,
        }
    }
    return {path: redirect || '/account'}
}

/**
 * Works out where somebody lands once they are signed in.
 *
 * <p>Three screens end in a session now: the sign-in form, the second factor, and setting a password
 * from a link. They must all land in the same place, because landing somewhere else is how somebody
 * concludes the wrong thing about which account they are in. The decision is made here and the
 * navigating is left to each screen, which is the part they genuinely differ on: the second factor
 * counts as a public path and has to leave by a full page load rather than through the router.
 *
 * <p>Where the reader asked for a particular page on the way in, that wins over all of it.
 *
 * @param redirect where the reader was headed before they were asked to sign in
 */
export async function decideSignInLanding(redirect?: string): Promise<SignInLanding> {
    return landingFromMemberships(await loadLandingMemberships(), redirect)
}
