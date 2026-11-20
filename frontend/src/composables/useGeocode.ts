/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import type {GeocodeAnswer, GeocodePrecision, GeocodeResult} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'

/** How a lookup came out: how close the pin is, or that nothing was found. */
export type GeocodeOutcome = GeocodePrecision | 'NOT_FOUND'

/**
 * One "Find on map" button's state: whether a lookup runs, how the last one came out and why it
 * failed. `find` resolves with the pin, or null when nothing was found or the lookup failed; the
 * caller moves its map, the composable never saves anything.
 *
 * @param lookup asks the server where the typed text lies
 */
export function useGeocode(lookup: () => Promise<GeocodeAnswer>) {
    const outcome = ref<GeocodeOutcome | null>(null)

    const {running, failure, run} = useAsyncAction(async (): Promise<GeocodeResult | null> => {
        outcome.value = null
        const answer = await lookup()
        outcome.value = answer.match ? answer.match.precision : 'NOT_FOUND'
        return answer.match ?? null
    })

    async function find(): Promise<GeocodeResult | null> {
        return (await run()) ?? null
    }

    function reset() {
        outcome.value = null
    }

    return {running, failure, outcome: readonly(outcome), find, reset}
}
