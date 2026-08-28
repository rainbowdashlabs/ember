/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import RepeatEndField from './RepeatEndField.vue'

/**
 * A last day and a number of times are two ways of saying the same thing, and the backend refuses
 * both at once. Which is why choosing one here clears the other rather than leaving it standing.
 */
describe('RepeatEndField', () => {
    it('starts without an end where nothing was said', async () => {
        const field = await mountSuspended(RepeatEndField, {props: {until: '', count: undefined}})

        const kind = field.get('[data-testid="repeat-end-kind"]').element as HTMLSelectElement
        expect(kind.value).toBe('never')
        expect(field.find('[data-testid="repeat-end-until"]').exists()).toBe(false)
        expect(field.find('[data-testid="repeat-end-count"]').exists()).toBe(false)
    })

    it('offers a number of times, and does not keep a day beside it', async () => {
        const field = await mountSuspended(RepeatEndField, {props: {until: '2026-09-16', count: undefined}})

        await field.get('[data-testid="repeat-end-kind"]').setValue('afterCount')

        expect(field.emitted('update:until')?.at(-1)).toEqual([''])
        expect(field.emitted('update:count')?.at(-1)).toEqual([10])
    })

    it('offers a day, and does not keep a number of times beside it', async () => {
        const field = await mountSuspended(RepeatEndField, {props: {until: '', count: 8}})

        const kind = field.get('[data-testid="repeat-end-kind"]').element as HTMLSelectElement
        expect(kind.value, 'a count that was given is what the field shows').toBe('afterCount')

        await field.get('[data-testid="repeat-end-kind"]').setValue('onDate')

        expect(field.emitted('update:count')?.at(-1)).toEqual([undefined])
    })
})
