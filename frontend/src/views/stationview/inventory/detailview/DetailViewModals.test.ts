/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {shallowMount} from '@vue/test-utils'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import type {InventoryItem} from '@/api/generated/schema'
import {followModifierKeys} from '@/util/modifierKeys'
import type {Failure} from '@/util/failure'
import DetailViewModals from './DetailViewModals.vue'

const deleteItem = vi.fn()
vi.mock('@/api', () => ({inventory: {deleteItem: (id: number) => deleteItem(id)}, procurement: {}}))

const JACKET = {id: 7, name: 'Jacke'} as InventoryItem

/** The page's own handle on the modals, as the detail view calls it. */
interface Opener {
    openDelete: (item: InventoryItem) => void
}

function modals(onError: (failure: Failure) => void = () => {}) {
    return shallowMount(DetailViewModals, {
        props: {detail: null, memberMap: new Map(), unassignedMembers: [], onError},
    })
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Deleting a piece from the inventory detail asks first, and shift skips the question. */
describe('DetailViewModals', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        deleteItem.mockReset().mockResolvedValue(undefined)
    })
    afterEach(() => holdShift(false))

    it('asks before deleting a piece and deletes once agreed', async () => {
        const wrapper = modals()

        ;(wrapper.vm as unknown as Opener).openDelete(JACKET)
        await wrapper.vm.$nextTick()

        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(true)
        expect(deleteItem).not.toHaveBeenCalled()

        wrapper.findComponent({name: 'ConfirmDeleteModal'}).vm.$emit('confirm')
        await vi.waitFor(() => expect(wrapper.emitted('reload')).toHaveLength(1))
        expect(deleteItem).toHaveBeenCalledWith(7)
    })

    it('deletes at once while shift is held and has the page reload', async () => {
        const wrapper = modals()

        holdShift(true)
        ;(wrapper.vm as unknown as Opener).openDelete(JACKET)
        await vi.waitFor(() => expect(wrapper.emitted('reload')).toHaveLength(1))

        expect(deleteItem).toHaveBeenCalledWith(7)
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })

    it('passes a refusal on to the page', async () => {
        deleteItem.mockRejectedValue(new Error('refused'))
        const failures: Failure[] = []
        const wrapper = modals(failure => failures.push(failure))

        holdShift(true)
        ;(wrapper.vm as unknown as Opener).openDelete(JACKET)

        await vi.waitFor(() => expect(failures).toHaveLength(1))
        expect(failures[0]!.message).not.toBe('')
        expect(wrapper.emitted('reload')).toBeUndefined()
    })
})
