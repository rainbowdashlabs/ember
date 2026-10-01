/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import FieldsStep from './FieldsStep.vue'
import {FieldTypes, type FieldTypeName} from '@/api/fieldTypes'
import type {ProfileField, ProfileFieldConfig} from '@/api/generated/schema'

const NO_SETTINGS: ProfileFieldConfig = {
    ageMode: null, computed: false, defaultValue: null, description: null, notifyOnChange: false, options: null,
    overview: false, reminderDays: null, remindManagement: null, remindMember: null, repeatEveryDays: null,
    showAge: null, sourceField: null, sourceFieldId: null, warnFromDays: null,
}

function question(id: number, name: string, fieldType: FieldTypeName, required = false): ProfileField {
    return {
        id, stationId: 'station', name, fieldType, config: NO_SETTINGS,
        required, readonly: false, keepOnArchive: false, width: null,
    }
}

/**
 * The questions a new member is asked.
 *
 * <p>This step drew the questions itself once, and a heading the station had put between them is
 * not a question: it came out as an empty box to type a heading into. Everything a station arranges
 * on its form is laid out by the one component that knows what each entry is, and the point of
 * these is that this step keeps going through it.
 */
describe('FieldsStep', () => {
    const heading = question(1, 'Kontakt', FieldTypes.SECTION)
    const phone = question(2, 'Telefon', FieldTypes.TEXT)

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
        const second = question(3, 'Mobil', FieldTypes.TEXT, true)
        const view = step([phone, second])

        const labels = view.findAll('label').map(label => label.text())
        expect(labels[0]).toContain('Telefon')
        expect(labels[1]).toContain('Mobil')
    })
})
