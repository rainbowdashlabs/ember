/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {useBackingOffPoll, type PollOutcome} from './useBackingOffPoll'

/** A rejection shaped the way axios rejects, which is what the helpers under test read. */
function refusal(status: number, retryAfterSeconds?: number) {
    return {response: {status, data: retryAfterSeconds ? {retryAfterSeconds} : {}}}
}

describe('useBackingOffPoll', () => {
    beforeEach(() => vi.useFakeTimers())
    afterEach(() => {
        vi.runOnlyPendingTimers()
        vi.useRealTimers()
    })

    it('keeps asking while the tick says the wait goes on', async () => {
        const tick = vi.fn<() => Promise<PollOutcome>>().mockResolvedValue('again')
        const poll = useBackingOffPoll(tick)

        poll.start()
        await vi.advanceTimersByTimeAsync(2500)
        await vi.advanceTimersByTimeAsync(2500)

        expect(tick).toHaveBeenCalledTimes(2)
        poll.stop()
    })

    it('stops asking once the tick says so', async () => {
        const tick = vi.fn<() => Promise<PollOutcome>>().mockResolvedValue('stop')
        const poll = useBackingOffPoll(tick)

        poll.start()
        await vi.advanceTimersByTimeAsync(2500)
        await vi.advanceTimersByTimeAsync(60000)

        expect(tick).toHaveBeenCalledTimes(1)
    })

    /**
     * The bug this exists for. A refusal for asking too often used to be swallowed and the wait kept
     * firing at the same rate, so two devices behind one address held each other refused for as long
     * as they both waited. Backing off is what lets the bucket refill.
     */
    it('waits longer after being told it is asking too often', async () => {
        const tick = vi.fn<() => Promise<PollOutcome>>().mockRejectedValue(refusal(429))
        const poll = useBackingOffPoll(tick)

        poll.start()
        await vi.advanceTimersByTimeAsync(2500)
        expect(tick, 'the first ask happened on the usual beat').toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(2500)
        expect(tick, 'and the next one did not, because the wait doubled').toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(2500)
        expect(tick).toHaveBeenCalledTimes(2)
        poll.stop()
    })

    it('waits as long as the server asked where it named a time', async () => {
        const tick = vi.fn<() => Promise<PollOutcome>>().mockRejectedValue(refusal(429, 20))
        const poll = useBackingOffPoll(tick)

        poll.start()
        await vi.advanceTimersByTimeAsync(2500)

        await vi.advanceTimersByTimeAsync(19000)
        expect(tick, 'nothing asked again inside the twenty seconds').toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(1000)
        expect(tick).toHaveBeenCalledTimes(2)
        poll.stop()
    })

    it('says it is being throttled, and stops saying so once it is let through', async () => {
        const tick = vi.fn<() => Promise<PollOutcome>>()
            .mockRejectedValueOnce(refusal(429))
            .mockResolvedValue('again')
        const poll = useBackingOffPoll(tick)

        poll.start()
        await vi.advanceTimersByTimeAsync(2500)
        expect(poll.throttled.value).toBe(true)

        await vi.advanceTimersByTimeAsync(5000)
        expect(poll.throttled.value).toBe(false)
        poll.stop()
    })

    /** Any other failure is nothing: the tick after it asks again on the usual beat. */
    it('treats an ordinary failure as a lost ask rather than an end', async () => {
        const tick = vi.fn<() => Promise<PollOutcome>>()
            .mockRejectedValueOnce(new Error('network'))
            .mockResolvedValue('again')
        const poll = useBackingOffPoll(tick)

        poll.start()
        await vi.advanceTimersByTimeAsync(2500)
        await vi.advanceTimersByTimeAsync(2500)

        expect(tick).toHaveBeenCalledTimes(2)
        expect(poll.throttled.value).toBe(false)
        poll.stop()
    })

    it('asks nothing more once it is stopped', async () => {
        const tick = vi.fn<() => Promise<PollOutcome>>().mockResolvedValue('again')
        const poll = useBackingOffPoll(tick)

        poll.start()
        poll.stop()
        await vi.advanceTimersByTimeAsync(60000)

        expect(tick).not.toHaveBeenCalled()
    })
})
