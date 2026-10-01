/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/** One screen asking for a station, under a token of its own. */
interface Claim {
    token: string
    uid: string | null
}

/**
 * The station a request should be answered for, when it is not the one the reader last picked.
 *
 * <p>An association keeps its knowledge base, its news and its calendar on the station it owns, and the
 * screens that edit them are the station's own screens. While one of those screens is open, the station
 * every request means is that one, whether or not the reader also belongs to a station of their own. What
 * the reader may do there is decided the same way, by what they hold at the association.
 *
 * <p>Held as the claims of the screens asking for it, newest last. Moving from one of the association's
 * screens to another mounts the next before it unmounts the last, so for a moment two of them are asking.
 * A single value plus "put back what was there" loses that race: the screen leaving restores nothing over
 * the screen arriving, and its first request goes out for no station at all. Each screen holds a claim of
 * its own instead, and what counts is the newest claim still standing.
 *
 * <p>Held per request, so a screen rendered on the server cannot leave its station behind for the next
 * reader. Set by {@code useActingStation}, read by the request client and by the permission checks. Take
 * it at the top of a setup or a composable, before anything is awaited.
 */
export function actingStationState() {
    const claims = useState<Claim[]>('actingStationState', () => [])

    return {
        /** Asks for a station under a token of the caller's own, or moves a claim already made. */
        claim(token: string, uid: string | null): void {
            const existing = claims.value.find(claim => claim.token === token)
            if (existing) existing.uid = uid
            else claims.value.push({token, uid})
        },
        /** Gives one up. What the last screen still asking for takes over, or nothing does. */
        release(token: string): void {
            claims.value = claims.value.filter(claim => claim.token !== token)
        },
        /** The station asked for by the newest claim still standing, or null. */
        current(): string | null {
            return claims.value.at(-1)?.uid ?? null
        },
    }
}

/**
 * The station the request client sends with every request, for code that runs outside any component.
 *
 * <p>Outside a running app, as in a unit test of the request client, no screen is open, so none acts for
 * a station.
 */
export function getActingStation(): string | null {
    return tryUseNuxtApp() ? actingStationState().current() : null
}
