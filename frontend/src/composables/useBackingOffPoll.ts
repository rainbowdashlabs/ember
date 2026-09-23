/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref, type Ref} from 'vue'
import {apiErrorStatus, retryAfterMillis} from '@/util/apiError'

/** What a tick decided: ask again after the current wait, or stop asking. */
export type PollOutcome = 'again' | 'stop'

const INTERVAL = 2500
const INTERVAL_MAX = 30000

export interface BackingOffPoll {
    start: () => void
    stop: () => void
    /** Whether the server has asked this client to slow down, so a screen can say why it waits. */
    throttled: Readonly<Ref<boolean>>
}

/**
 * A wait that asks again until it is told to stop, and slows down when it is asked to.
 *
 * <p>Both halves of the device handshake wait like this, and both used to swallow a refusal for
 * asking too often and keep asking at the same rate. That is what made the limit impossible to
 * clear while the screen was open: two devices behind one address kept each other refused for as
 * long as they both waited. Backing off is what lets the bucket refill.
 *
 * <p>The server's own retry-after is used where it named one, and a doubling up to half a minute
 * where it did not. Any other failure is nothing: the next tick asks again, exactly as before.
 *
 * @param tick one ask, returning whether the wait goes on
 */
export function useBackingOffPoll(tick: () => Promise<PollOutcome>): BackingOffPoll {
    const throttled = ref(false)
    const interval = ref(INTERVAL)
    let timer: ReturnType<typeof setTimeout> | null = null

    function stop() {
        if (timer) clearTimeout(timer)
        timer = null
    }

    function schedule() {
        stop()
        timer = setTimeout(run, interval.value)
    }

    async function run() {
        try {
            const outcome = await tick()
            throttled.value = false
            interval.value = INTERVAL
            if (outcome === 'again') schedule()
        } catch (e) {
            if (apiErrorStatus(e) === 429) {
                throttled.value = true
                interval.value = Math.min(retryAfterMillis(e) ?? interval.value * 2, INTERVAL_MAX)
            }
            schedule()
        }
    }

    function start() {
        throttled.value = false
        interval.value = INTERVAL
        schedule()
    }

    return {start, stop, throttled: readonly(throttled)}
}
