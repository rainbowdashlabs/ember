/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {followModifierKeys} from '@/util/modifierKeys'
import AiSettingsPanel from './AiSettingsPanel.vue'

const deleteAiCredential = vi.fn()
vi.mock('@/api', () => ({
    ai: {
        getAiCredential: async () => ({provider: 'OPENAI', model: null, usable: true}),
        getSettings: async () => ({providers: []}),
        deleteAiCredential: () => deleteAiCredential(),
    },
}))

async function panelOpened() {
    const wrapper = shallowMount(AiSettingsPanel, {global: {renderStubDefaultSlot: true}})
    await flushPromises()
    wrapper.findComponent(SecondaryButton).vm.$emit('click')
    await wrapper.vm.$nextTick()
    return wrapper
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Removing the stored key asks first, and shift skips the question. */
describe('AiSettingsPanel', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        deleteAiCredential.mockReset().mockResolvedValue(undefined)
    })
    afterEach(() => holdShift(false))

    it('asks before removing the stored key', async () => {
        const wrapper = await panelOpened()

        wrapper.findComponent(ErrorButton).vm.$emit('click')
        await wrapper.vm.$nextTick()

        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(true)
        expect(deleteAiCredential).not.toHaveBeenCalled()

        wrapper.findComponent({name: 'ConfirmDeleteModal'}).vm.$emit('confirm')
        await vi.waitFor(() => expect(deleteAiCredential).toHaveBeenCalledOnce())
    })

    it('removes the key at once while shift is held', async () => {
        const wrapper = await panelOpened()

        holdShift(true)
        wrapper.findComponent(ErrorButton).vm.$emit('click')

        await vi.waitFor(() => expect(deleteAiCredential).toHaveBeenCalledOnce())
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })
})
