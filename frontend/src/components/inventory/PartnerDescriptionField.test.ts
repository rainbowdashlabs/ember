/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import PartnerDescriptionField from './PartnerDescriptionField.vue'

function field(text: string) {
    return mount(PartnerDescriptionField, {props: {modelValue: text}})
}

/** The description field shows how the lending shop renders the text, and nothing while it is empty. */
describe('PartnerDescriptionField', () => {
    it('shows no preview while nothing is written', () => {
        expect(field('   ').find('[data-testid="partner-description-preview"]').exists()).toBe(false)
    })

    it('renders the Markdown in the preview', () => {
        const preview = field('**Aufbau** zu zweit').find('[data-testid="partner-description-preview"]')

        expect(preview.exists()).toBe(true)
        expect(preview.find('strong').text()).toBe('Aufbau')
    })
})
