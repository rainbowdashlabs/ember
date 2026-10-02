/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {usePagedList} from './usePagedList'

/** A promise plus the handle that settles it, so a test can decide which page arrives first. */
function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>(res => {
        resolve = res
    })
    return {promise, resolve}
}

/** A paged list whose every request waits until the test answers it. */
function heldList() {
    const requests: ReturnType<typeof deferred<string[]>>[] = []
    const list = usePagedList<string>(() => {
        const request = deferred<string[]>()
        requests.push(request)
        return request.promise
    }, 2)
    return {requests, ...list}
}

/**
 * A list whose filter changed while an earlier page was still on its way.
 *
 * @vitest-environment happy-dom
 */
describe('usePagedList', () => {
    it('keeps the newer first page when the older one lands last', async () => {
        const {requests, items, load} = heldList()

        const older = load()
        const newer = load()
        requests[1]!.resolve(['new'])
        await newer
        requests[0]!.resolve(['old', 'older'])
        await older

        expect(items.value).toEqual(['new'])
    })

    it('drops a next page fetched before the list was loaded again', async () => {
        const {requests, items, load, loadMore} = heldList()
        const first = load()
        requests[0]!.resolve(['a', 'b'])
        await first

        const more = loadMore()
        const reloaded = load()
        requests[2]!.resolve(['x'])
        await reloaded
        requests[1]!.resolve(['c', 'd'])
        await more

        expect(items.value).toEqual(['x'])
    })
})
