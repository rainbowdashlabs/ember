/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it, vi} from 'vitest'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import CreateBlockModal from './CreateBlockModal.vue'

const createBlock = vi.fn()
vi.mock('@/api/lending', () => ({createBlock: (request: unknown) => createBlock(request)}))

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

/** A drag's days are set aside with the reason given, for the product or the whole station. */
describe('CreateBlockModal', () => {
    it('sets the days aside with the reason and closes', async () => {
        createBlock.mockResolvedValue({})
        const wrapper = shallowMount(CreateBlockModal, {
            props: {modelValue: true, from: '2030-06-03', to: '2030-06-05', inventoryId: null, label: 'die ganze Wache'},
            global: {plugins: [i18n], renderStubDefaultSlot: true},
        })

        await wrapper.findComponent(TextInput).vm.$emit('update:modelValue', 'Fest')
        await wrapper.findComponent(PrimaryButton).vm.$emit('click')
        await flushPromises()

        expect(createBlock).toHaveBeenCalledWith({inventoryId: null, itemId: null, blockFrom: '2030-06-03', blockTo: '2030-06-05', reason: 'Fest'})
        expect(wrapper.emitted('created')).toHaveLength(1)
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
    })
})
