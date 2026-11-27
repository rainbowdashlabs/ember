/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import ShownIfField from './ShownIfField.vue'
import type {EditableField} from '@/api/profileFields'

function question(id: number, name: string, fieldType: string, fieldSetId: number | null = null): EditableField {
    return {id, name, fieldType, fieldSetId} as EditableField
}

/**
 * The picker shows what lets the dependent question in, so a choice is not taken for a yes/no question.
 */
describe('ShownIfField', () => {
    it('names a yes/no question with its yes and a choice with any answer, and leaves out the one edited', async () => {
        const wrapper = await mountSuspended(ShownIfField, {
            props: {
                modelValue: null,
                conditionFields: [
                    question(1, 'JuLeiCa', 'BOOLEAN'),
                    question(2, 'Führerscheinklasse', 'CHOICE'),
                    question(3, 'Bemerkung', 'TEXT'),
                ],
                fieldId: 3,
            },
        })

        const options = wrapper.findAll('option').map(option => option.text())

        expect(options).toContain('JuLeiCa (bei Ja)')
        expect(options).toContain('Führerscheinklasse (wenn beantwortet)')
        expect(options.some(option => option.startsWith('Bemerkung'))).toBe(false)
    })

    it('offers a question in a group only the questions of that group, and keeps one outside it in view', async () => {
        const wrapper = await mountSuspended(ShownIfField, {
            props: {
                modelValue: 4,
                conditionFields: [question(4, 'Bemerkung', 'TEXT'), question(5, 'Klasse', 'CHOICE', 9)],
                fieldId: 6,
                setId: 9,
            },
        })

        const options = wrapper.findAll('option').map(option => option.text())
        expect(options).toContain('Klasse (wenn beantwortet)')
        expect(options).toContain('Bemerkung (außerhalb der Gruppe)')
        expect(wrapper.find('[data-testid="field-shown-if-outside"]').exists()).toBe(true)
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    })
})
