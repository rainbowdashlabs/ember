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
import ItemReturnAndLossModals from './ItemReturnAndLossModals.vue'

const deleteItem = vi.fn()
const assignItem = vi.fn()
const markLost = vi.fn()
vi.mock('@/api', () => ({
    inventory: {
        deleteItem: (id: number) => deleteItem(id),
        assignItem: (id: number, request: unknown) => assignItem(id, request),
        markLost: (id: number) => markLost(id),
    },
}))

const JACKET = {id: 7, name: 'Jacke'} as InventoryItem

/** The component's own handle, as the inventory lists call it. */
interface Handle {
    unassign: (item: InventoryItem) => Promise<void>
    markLost: (item: InventoryItem) => void
}

function modals(external: boolean) {
    return shallowMount(ItemReturnAndLossModals, {props: {external}})
}

function handle(wrapper: ReturnType<typeof modals>) {
    return wrapper.vm as unknown as Handle
}

function question(wrapper: ReturnType<typeof modals>, message: string) {
    return wrapper.findAllComponents({name: 'ConfirmDeleteModal'}).find(modal => String(modal.props('message')).includes(message))!
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Taking a piece back and marking it lost, with the questions they ask and shift skipping them. */
describe('ItemReturnAndLossModals', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        deleteItem.mockReset().mockResolvedValue(undefined)
        assignItem.mockReset().mockResolvedValue(undefined)
        markLost.mockReset().mockResolvedValue(JACKET)
    })
    afterEach(() => holdShift(false))

    it('asks before taking back a piece of an external inventory, which deletes it', async () => {
        const wrapper = modals(true)

        await handle(wrapper).unassign(JACKET)
        await wrapper.vm.$nextTick()

        const ask = question(wrapper, 'zurücknehmen')
        expect(ask.props('modelValue')).toBe(true)
        expect(deleteItem).not.toHaveBeenCalled()

        ask.vm.$emit('confirm')
        await vi.waitFor(() => expect(wrapper.emitted('changed')).toHaveLength(1))
        expect(deleteItem).toHaveBeenCalledWith(7)
    })

    it('takes back a piece of an external inventory at once while shift is held', async () => {
        const wrapper = modals(true)

        holdShift(true)
        await handle(wrapper).unassign(JACKET)

        await vi.waitFor(() => expect(wrapper.emitted('changed')).toHaveLength(1))
        expect(deleteItem).toHaveBeenCalledWith(7)
        expect(question(wrapper, 'zurücknehmen').props('modelValue')).toBe(false)
    })

    it('takes back a piece of the own stock without asking, since assigning it again undoes it', async () => {
        const wrapper = modals(false)

        await handle(wrapper).unassign(JACKET)

        expect(assignItem).toHaveBeenCalledWith(7, {memberId: null, memberName: ''})
        expect(deleteItem).not.toHaveBeenCalled()
        expect(wrapper.emitted('changed')).toHaveLength(1)
    })

    it('asks before marking a piece lost, and shift skips the question', async () => {
        const wrapper = modals(false)

        handle(wrapper).markLost(JACKET)
        await wrapper.vm.$nextTick()
        expect(question(wrapper, 'verloren').props('modelValue')).toBe(true)
        expect(question(wrapper, 'verloren').props('confirmLabel')).toBe('Als verloren markieren')
        expect(markLost).not.toHaveBeenCalled()
        question(wrapper, 'verloren').vm.$emit('update:modelValue', false)

        holdShift(true)
        handle(wrapper).markLost(JACKET)
        await vi.waitFor(() => expect(markLost).toHaveBeenCalledWith(7))
        await vi.waitFor(() => expect(wrapper.emitted('changed')).toHaveLength(1))
    })

    it('passes a refusal on to the list', async () => {
        deleteItem.mockRejectedValue(new Error('refused'))
        const wrapper = modals(true)

        holdShift(true)
        await handle(wrapper).unassign(JACKET)

        await vi.waitFor(() => expect(wrapper.emitted('error')).toHaveLength(1))
        expect(wrapper.emitted('changed')).toBeUndefined()
    })
})
