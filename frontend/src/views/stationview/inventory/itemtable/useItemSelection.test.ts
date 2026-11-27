/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {useItemSelection} from './useItemSelection'

/** The ticks of the select mode, which outlive a reload and forget what is gone. */
describe('useItemSelection', () => {
    it('ticks and unticks one piece at a time', () => {
        const selection = useItemSelection()
        selection.toggle(1)
        selection.toggle(2)
        selection.toggle(1)

        expect(selection.selectedIds.value).toEqual([2])
        expect(selection.count.value).toBe(1)
    })

    it('ticks all shown pieces, and unticks them once all are ticked', () => {
        const selection = useItemSelection()
        selection.toggle(3)

        expect(selection.someOf([1, 2, 3])).toBe(true)
        selection.toggleAll([1, 2, 3])
        expect(selection.allOf([1, 2, 3])).toBe(true)
        selection.toggleAll([1, 2, 3])
        expect(selection.count.value).toBe(0)
        expect(selection.allOf([])).toBe(false)
    })

    it('keeps only the pieces that are still there', () => {
        const selection = useItemSelection()
        selection.toggleAll([1, 2, 3])
        selection.keepOnly([2, 3, 4])

        expect(selection.selectedIds.value.sort()).toEqual([2, 3])
    })

    it('forgets the ticks when the mode is switched off', () => {
        const selection = useItemSelection()
        selection.setActive(true)
        selection.toggle(5)
        selection.setActive(false)

        expect(selection.active.value).toBe(false)
        expect(selection.count.value).toBe(0)
    })
})
