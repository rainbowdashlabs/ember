/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'

/**
 * The station a request should be answered for, when it is not the one the reader last picked.
 *
 * <p>An association keeps its knowledge base, its news and its calendar on the station it owns, and the
 * screens that edit them are the station's own screens. While one of those screens is open, the station
 * every request means is that one, whether or not the reader also belongs to a station of their own. What
 * the reader may do there is decided the same way, by what they hold at the association.
 *
 * <p>Set by {@code useActingStation}, read by the request client and by the permission checks, and nothing
 * else touches it. It is a ref so that a check made once, in a computed, notices when it changes.
 */
const actingStationUid = ref<string | null>(null)

/**
 * Who is asking for it, newest last.
 *
 * <p>Moving from one of the association's screens to another mounts the next before it unmounts the last,
 * so for a moment two of them are asking. A single value plus "put back what was there" loses that race:
 * the screen leaving restores nothing over the screen arriving, and its first request goes out for no
 * station at all. Each screen holds a claim of its own instead, and what counts is the newest claim still
 * standing.
 */
const claims: Array<{token: symbol; uid: string | null}> = []

function settle(): void {
    actingStationUid.value = claims.length === 0 ? null : claims[claims.length - 1]!.uid
}

/** Asks for a station under a token of the caller's own, or moves a claim already made. */
export function claimActingStation(token: symbol, uid: string | null): void {
    const existing = claims.find(claim => claim.token === token)
    if (existing) existing.uid = uid
    else claims.push({token, uid})
    settle()
}

/** Gives one up. What the last screen still asking for takes over, or nothing does. */
export function releaseActingStation(token: symbol): void {
    const at = claims.findIndex(claim => claim.token === token)
    if (at >= 0) claims.splice(at, 1)
    settle()
}

export function getActingStation(): string | null {
    return actingStationUid.value
}
