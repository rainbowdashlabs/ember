/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import FieldsStep from './FieldsStep.vue'
import {FieldTypes, type ProfileField} from '@/api/profileFields'

/**
 * The questions a new member is asked.
 *
 * <p>This step drew the questions itself once, and a heading the station had put between them is
 * not a question: it came out as an empty box to type a heading into. Everything a station arranges
 * on its form is laid out by the one component that knows what each entry is, and the point of
 * these is that this step keeps going through it.
 */
describe('FieldsStep', () => {
    const heading: ProfileField = {id: 1, name: 'Kontakt', fieldType: FieldTypes.SECTION}
    const phone: ProfileField = {id: 2, name: 'Telefon', fieldType: FieldTypes.TEXT}

    function step(fields: ProfileField[]) {
        return mount(FieldsStep, {
            props: {fields, values: new Map<number, string>()},
            global: {
                mocks: {$t: (key: string) => key},
                stubs: {PrimaryButton: true, SecondaryButton: true, ButtonRow: true},
            },
        })
    }

    it('draws a heading as a heading rather than as something to fill in', () => {
        const view = step([heading, phone])

        expect(view.findAll('input')).toHaveLength(1)
        expect(view.text()).toContain('Kontakt')
    })

    it('keeps the order the station arranged its questions in', () => {
        const second: ProfileField = {id: 3, name: 'Mobil', fieldType: FieldTypes.TEXT, required: true}
        const view = step([phone, second])

        const labels = view.findAll('label').map(label => label.text())
        expect(labels[0]).toContain('Telefon')
        expect(labels[1]).toContain('Mobil')
    })
})
