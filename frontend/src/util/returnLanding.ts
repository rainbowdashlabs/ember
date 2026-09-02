/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getItem} from '@/api/storage'
import {accountKey, readLandingMemory} from '@/util/landingMemoryState'
import {
    type LandingMemberships,
    type SignInLanding,
    landingFromMemberships,
    loadLandingMemberships,
} from '@/util/signInLanding'

/**
 * The remembered area, but only while the account still reaches it.
 *
 * <p>Everything a note can outlive is checked against what the account may act for right now: a
 * station it was thrown out of, an association that was dissolved, an administration right that was
 * taken away. Sending somebody to a place that turns them away again is how a landing page becomes a
 * roundabout, so a note that no longer holds counts as no note at all.
 *
 * @param memberships what the account may act for
 */
function rememberedLanding(memberships: LandingMemberships): SignInLanding | null {
    const memory = readLandingMemory()
    if (!memory) return null
    const signedIn = accountKey(memberships.info?.account)
    if (!signedIn || signedIn !== memory.account) return null

    if (memory.area === 'admin') {
        if (memberships.info?.instanceUserType !== 'ADMINISTRATOR') return null
        return {path: '/admin/dashboard/overview'}
    }
    if (memory.area === 'cluster') {
        if (memberships.clusters.length === 0) return null
        const remembered = memberships.clusters.find(cluster => cluster.uid === memory.clusterUid)
        return {path: '/cluster', clusterUid: remembered?.uid}
    }
    const station = memberships.stations.find(membership => membership.stationId === memory.stationId)
    if (!station) return null
    return {path: '/station/dashboard/overview', stationId: station.stationId}
}

/**
 * Where somebody who is already signed in goes when they open the landing page, and nowhere at all
 * for anybody who is not: the landing page is what a visitor came for.
 *
 * <p>The area they were last in comes first, because that is the answer to what they came back for.
 * Where nothing is remembered, or what is remembered is no longer theirs, the same decision that
 * follows a sign-in applies.
 */
export async function decideReturnLanding(): Promise<SignInLanding | null> {
    if (!getItem('session_token')) return null
    const memberships = await loadLandingMemberships()
    return rememberedLanding(memberships) ?? landingFromMemberships(memberships)
}
