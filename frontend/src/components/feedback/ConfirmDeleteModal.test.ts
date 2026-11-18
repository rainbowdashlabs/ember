/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {defineComponent} from 'vue'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import ConfirmDeleteModal from './ConfirmDeleteModal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'

const ModalStub = defineComponent({
    name: 'Modal',
    props: {modelValue: Boolean},
    template: '<div v-if="modelValue"><slot/></div>',
})

function dialog(neutral: boolean) {
    return mount(ConfirmDeleteModal, {
        props: {modelValue: true, message: 'Sicher?', confirmLabel: 'Speichern', neutral},
        global: {
            stubs: {Modal: ModalStub},
            plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}})],
        },
    })
}

/**
 * The one "are you sure" dialog asks with a red button before something goes, and with the primary one for a
 * question that removes nothing; either way the button confirms.
 *
 * @vitest-environment happy-dom
 */
describe('ConfirmDeleteModal', () => {
    it('asks with a red button by default', () => {
        const wrapper = dialog(false)
        expect(wrapper.findComponent(ErrorButton).exists()).toBe(true)
        expect(wrapper.findComponent(PrimaryButton).exists()).toBe(false)
    })

    it('asks with the primary button for a question that removes nothing', async () => {
        const wrapper = dialog(true)
        expect(wrapper.findComponent(ErrorButton).exists()).toBe(false)

        await wrapper.get('[data-confirm]').trigger('click')

        expect(wrapper.findComponent(PrimaryButton).text()).toContain('Speichern')
        expect(wrapper.emitted('confirm')).toHaveLength(1)
    })
})
