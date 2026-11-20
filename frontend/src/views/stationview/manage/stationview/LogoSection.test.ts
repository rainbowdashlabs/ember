/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {shallowMount} from '@vue/test-utils'
import {afterEach, beforeAll, describe, expect, it} from 'vitest'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {followModifierKeys} from '@/util/modifierKeys'
import LogoSection from './LogoSection.vue'

function section() {
    return shallowMount(LogoSection, {
        props: {hasLogo: true, logoObjectUrl: null, uploading: false, logoError: null, maxSize: 1024},
        global: {renderStubDefaultSlot: true},
    })
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Removing the logo asks first, and the page hears of it only once agreed or with shift held. */
describe('LogoSection', () => {
    beforeAll(() => followModifierKeys())
    afterEach(() => holdShift(false))

    it('asks before the logo is removed', async () => {
        const wrapper = section()

        wrapper.findComponent(DeleteButton).vm.$emit('click')
        await wrapper.vm.$nextTick()

        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(true)
        expect(wrapper.emitted('remove')).toBeUndefined()

        wrapper.findComponent({name: 'ConfirmDeleteModal'}).vm.$emit('confirm')
        await wrapper.vm.$nextTick()
        expect(wrapper.emitted('remove')).toHaveLength(1)
    })

    it('removes it at once while shift is held', async () => {
        const wrapper = section()

        holdShift(true)
        wrapper.findComponent(DeleteButton).vm.$emit('click')
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted('remove')).toHaveLength(1)
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })
})
