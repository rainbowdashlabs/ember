/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {shallowMount} from '@vue/test-utils'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {followModifierKeys} from '@/util/modifierKeys'
import AvatarSection from './AvatarSection.vue'

const deleteAvatar = vi.fn()
vi.mock('@/api', () => ({session: {deleteAvatar: () => deleteAvatar(), uploadAvatar: vi.fn()}}))

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Deleting the own picture asks first, and shift skips the question. */
describe('AvatarSection', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        deleteAvatar.mockReset().mockResolvedValue(undefined)
    })
    afterEach(() => holdShift(false))

    it('asks before deleting the picture and deletes it once agreed', async () => {
        const wrapper = shallowMount(AvatarSection, {props: {name: 'Anna'}, global: {renderStubDefaultSlot: true}})

        wrapper.findComponent(DeleteButton).vm.$emit('click')
        await wrapper.vm.$nextTick()

        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(true)
        expect(deleteAvatar).not.toHaveBeenCalled()

        wrapper.findComponent({name: 'ConfirmDeleteModal'}).vm.$emit('confirm')
        await vi.waitFor(() => expect(deleteAvatar).toHaveBeenCalledOnce())
    })

    it('deletes at once while shift is held', async () => {
        const wrapper = shallowMount(AvatarSection, {props: {name: 'Anna'}, global: {renderStubDefaultSlot: true}})

        holdShift(true)
        wrapper.findComponent(DeleteButton).vm.$emit('click')

        await vi.waitFor(() => expect(deleteAvatar).toHaveBeenCalledOnce())
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })
})
