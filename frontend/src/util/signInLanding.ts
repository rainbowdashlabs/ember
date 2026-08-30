/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {clusters, session} from '@/api'

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
    const [stations, info, myClusters] = await Promise.all([
        session.getStations(),
        session.getSessionInfo().catch(() => null),
        clusters.listMine().catch(() => []),
    ])

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
    const [onlyCluster] = myClusters
    if (myClusters.length > 0) {
        return {
            path: redirect || '/cluster',
            clusterUid: myClusters.length === 1 && onlyCluster ? onlyCluster.uid : undefined,
        }
    }
    return {path: redirect || '/account'}
}
