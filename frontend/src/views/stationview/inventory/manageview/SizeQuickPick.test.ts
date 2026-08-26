/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import SizeQuickPick from './SizeQuickPick.vue'

/**
 * The order sizes go in is the whole point of the field.
 *
 * <p>What is recorded first is the smallest, because everything else reads the order that way: one size
 * larger is the next one along. Somebody dragging from right to left, or clicking about, would otherwise
 * write their sizes down backwards and every exchange after that would offer a smaller one.
 */
describe('SizeQuickPick', () => {
    it('offers the four ranges a station stocks', async () => {
        const field = await mountSuspended(SizeQuickPick)
        const labels = field.findAll('button').map(button => button.text())

        expect(labels).toContain('4XS')
        expect(labels).toContain('4XL')
        expect(labels, 'body heights in sixes').toEqual(expect.arrayContaining(['116', '122', '188']))
        expect(labels, 'chest sizes in twos').toEqual(expect.arrayContaining(['44', '46', '74']))
        expect(labels, 'the short ones in fours').toEqual(expect.arrayContaining(['90', '94', '122']))
        expect(labels, 'nothing between the steps of any row').not.toContain('117')
        expect(labels).not.toContain('45')
    })

    it('hands the picked sizes over in the order they are shown, not the order they were touched', async () => {
        const field = await mountSuspended(SizeQuickPick)

        for (const label of ['L', 'S', 'XL']) {
            await field.get(`[data-testid="size-box-${label}"]`).trigger('click')
        }
        await field.get('[data-testid="size-quick-add"]').trigger('click')

        expect(field.emitted('add')?.[0]).toEqual([['S', 'L', 'XL']])
    })

    it('names a size that two rows share only once', async () => {
        const field = await mountSuspended(SizeQuickPick)

        await field.get('[data-testid="size-box-122"]').trigger('click')
        await field.get('[data-testid="size-quick-add"]').trigger('click')

        expect(field.emitted('add')?.[0], '122 is both a body height and a short size').toEqual([['122']])
    })

    it('takes everything a drag crosses and lets a click take one back', async () => {
        const field = await mountSuspended(SizeQuickPick)

        await field.get('[data-testid="size-box-116"]').trigger('mousedown')
        await field.get('[data-testid="size-box-122"]').trigger('mouseenter')
        await field.get('[data-testid="size-box-128"]').trigger('mouseenter')
        window.dispatchEvent(new MouseEvent('mouseup'))

        await field.get('[data-testid="size-box-122"]').trigger('click')
        await field.get('[data-testid="size-quick-add"]').trigger('click')

        expect(field.emitted('add')?.[0]).toEqual([['116', '128']])
    })
})
