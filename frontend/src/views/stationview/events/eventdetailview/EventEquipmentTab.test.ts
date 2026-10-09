/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import EventEquipmentTab from './EventEquipmentTab.vue'

const get = vi.fn()

vi.mock('@/api/client', () => ({
    default: {get: (...args: unknown[]) => get(...args)},
}))

vi.mock('vue-router', async importOriginal => ({
    ...await importOriginal<typeof import('vue-router')>(),
    useRouter: () => ({push: vi.fn()}),
}))

/** Answers every read the tab makes, so what is asked for is all that is left to look at. */
function answer(url: string) {
    if (url.endsWith('/equipment/choices')) return Promise.resolve({data: {inventories: [], arts: [], items: []}})
    return Promise.resolve({data: []})
}

function requested(): string[] {
    return get.mock.calls.map(call => call[0] as string)
}

/**
 * Whoever may edit an appointment chooses its gear without the right to read the inventory. The
 * tab used to fill its pickers from the inventory itself, which such a reader was refused, and the
 * refusal reached them as an error every time the tab opened.
 */
describe('EventEquipmentTab', () => {
    beforeEach(() => {
        get.mockReset()
        get.mockImplementation(answer)
    })

    function tab(canEdit: boolean) {
        return mount(EventEquipmentTab, {
            props: {eventId: 5, effectiveDate: '2026-09-04', recurring: false, canEdit, canBorrow: false},
        })
    }

    it('fills the pickers from the appointment, never from the inventory', async () => {
        tab(true)
        await flushPromises()

        expect(requested()).toContain('/events/5/equipment/choices')
        expect(requested().filter(url => url.startsWith('/inventories'))).toEqual([])
    })

    it('reads no choices for a reader who cannot add a line', async () => {
        tab(false)
        await flushPromises()

        expect(requested()).toEqual(['/events/5/equipment/coverage'])
    })
})
