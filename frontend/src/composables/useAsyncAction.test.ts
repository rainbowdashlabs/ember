/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it, vi} from 'vitest'
import {useAsyncAction} from './useAsyncAction'

vi.mock('vue-i18n', () => ({useI18n: () => ({t: (key: string) => key})}))

/** A promise plus the handle that settles it, so a test can hold an action open. */
function deferred<T>() {
    let resolve!: (value: T) => void
    let reject!: (reason: unknown) => void
    const promise = new Promise<T>((res, rej) => {
        resolve = res
        reject = rej
    })
    return {promise, resolve, reject}
}

describe('useAsyncAction', () => {
    it('drops a second call made while the first is still running', async () => {
        const gate = deferred<string>()
        const fn = vi.fn(() => gate.promise)
        const {run} = useAsyncAction(fn)

        const first = run()
        const second = run()
        gate.resolve('done')

        expect(await first).toBe('done')
        expect(await second).toBeUndefined()
        expect(fn).toHaveBeenCalledTimes(1)
    })

    /**
     * The reason `coalesce` exists: several controls saving one object must not lose the change made
     * while an earlier save was still in the air.
     */
    it('runs a coalesced call once the first finishes, with its own arguments', async () => {
        const firstGate = deferred<string>()
        const secondGate = deferred<string>()
        let call = 0
        const seen: string[] = []
        const fn = vi.fn((value: string) => {
            seen.push(value)
            return call++ === 0 ? firstGate.promise : secondGate.promise
        })
        const {run} = useAsyncAction(fn, {coalesce: true})

        const first = run('first')
        void run('second')
        firstGate.resolve('a')
        await first
        await Promise.resolve()

        expect(seen).toEqual(['first', 'second'])
        secondGate.resolve('b')
    })

    /**
     * Only the last waiting call survives: catching up by replaying every save anybody asked for
     * would send the same state three times over.
     */
    it('keeps only the most recent waiting call', async () => {
        const firstGate = deferred<string>()
        const secondGate = deferred<string>()
        let call = 0
        const seen: string[] = []
        const fn = vi.fn((value: string) => {
            seen.push(value)
            return call++ === 0 ? firstGate.promise : secondGate.promise
        })
        const {run} = useAsyncAction(fn, {coalesce: true})

        const first = run('first')
        void run('second')
        void run('third')
        firstGate.resolve('a')
        await first
        await Promise.resolve()

        expect(seen).toEqual(['first', 'third'])
        secondGate.resolve('b')
    })

    it('reports a failure and stops running', async () => {
        const fn = vi.fn(() => Promise.reject(new Error('boom')))
        const {run, error, running} = useAsyncAction(fn)

        expect(await run()).toBeUndefined()
        expect(error.value).toBe('common.error')
        expect(running.value).toBe(false)
    })

    it('prefers the message the backend sent', async () => {
        const fn = vi.fn(() => Promise.reject({response: {data: {message: 'no room left'}}}))
        const {run, error} = useAsyncAction(fn)

        await run()
        expect(error.value).toBe('no room left')
    })
})
