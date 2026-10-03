/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {useDocumentPruning} from './useDocumentPruning'

const listIds = vi.fn()
const prune = vi.fn()

vi.mock('@/api', () => ({
    documents: {
        listIds: (...args: unknown[]) => listIds(...args),
        prune: (...args: unknown[]) => prune(...args),
    },
}))

/** Choosing documents across every page and deleting them in one go. */
describe('useDocumentPruning', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        listIds.mockResolvedValue([3, 5, 8])
        prune.mockResolvedValue(undefined)
    })

    it('chooses every document the current filter matches', async () => {
        const pruning = useDocumentPruning(() => ({departed: true}))

        await pruning.selectAll()

        expect(listIds).toHaveBeenCalledWith({departed: true})
        expect(pruning.selected.value).toEqual([3, 5, 8])
    })

    it('deletes what is chosen and lets go of the choice', async () => {
        const pruning = useDocumentPruning(() => ({}))
        pruning.selected.value = [3, 5]
        pruning.confirming.value = true

        const count = await pruning.prune()

        expect(prune).toHaveBeenCalledWith([3, 5])
        expect(count).toBe(2)
        expect(pruning.selected.value).toEqual([])
        expect(pruning.confirming.value).toBe(false)
    })

    it('keeps the choice when deleting was refused', async () => {
        prune.mockRejectedValue(new Error('refused'))
        const pruning = useDocumentPruning(() => ({}))
        pruning.selected.value = [3]

        await expect(pruning.prune()).rejects.toThrow('refused')

        expect(pruning.selected.value).toEqual([3])
        expect(pruning.busy.value).toBe(false)
    })
})
