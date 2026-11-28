/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {WaitingListEntryWithScore} from '@/api/generated/schema'
import {markShownAsSeen} from './markShownAsSeen'

const markSeen = vi.hoisted(() => vi.fn())

vi.mock('@/api', () => ({waitingList: {markSeen}}))

function row(id: number): WaitingListEntryWithScore {
    return {entry: {id}} as WaitingListEntryWithScore
}

/**
 * Opening a list takes what it shows off the reader's sidebar count, up to the newest entry shown.
 *
 * @vitest-environment happy-dom
 */
describe('markShownAsSeen', () => {
    beforeEach(() => {
        markSeen.mockReset()
        markSeen.mockResolvedValue(undefined)
    })

    it('records the newest entry the page shows', () => {
        markShownAsSeen(5, [row(3), row(9), row(4)])

        expect(markSeen).toHaveBeenCalledWith(5, 9)
    })

    it('records nothing for an empty list', () => {
        markShownAsSeen(5, [])

        expect(markSeen).not.toHaveBeenCalled()
    })

    it('leaves the page alone when recording fails', async () => {
        markSeen.mockRejectedValue(new Error('gone'))

        expect(() => markShownAsSeen(5, [row(1)])).not.toThrow()
        await Promise.resolve()
    })
})
