/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import ProfileFieldsLayout, {type LaidOutField} from './ProfileFieldsLayout.vue'
import {FieldTypes} from '@/api/profileFields'

/**
 * The form a member's answers are given on.
 *
 * <p>What is settled here is the question nobody answers: an age is worked out from a date, so the
 * form shows the number it comes to and does not offer it to be typed over. What was once typed
 * into it, before it began working itself out, is not what it shows.
 */
describe('ProfileFieldsLayout', () => {
    const birthDate: LaidOutField = {id: 10, name: 'Geburtsdatum', fieldType: FieldTypes.BIRTH_DATE}
    const age: LaidOutField = {
        id: 12,
        name: 'Alter',
        fieldType: FieldTypes.AGE,
        config: {sourceFieldId: 10, ageMode: 'now'},
    }

    function form(values: Record<number, string>) {
        return mount(ProfileFieldsLayout, {
            props: {
                fields: [birthDate, age],
                getValue: (field: LaidOutField) => values[field.id] ?? '',
            },
        })
    }

    function inputs(view: ReturnType<typeof form>) {
        return view.findAll('input')
    }

    it('works the age out rather than showing what was typed into it', () => {
        const view = form({10: '2010-05-14', 12: '99'})

        const ageInput = inputs(view)[1]!
        expect(ageInput.attributes('value') ?? (ageInput.element as HTMLInputElement).value)
            .not.toBe('99')
        expect(Number((ageInput.element as HTMLInputElement).value)).toBeGreaterThan(10)
    })

    it('does not offer a worked out answer to be written', () => {
        const view = form({10: '2010-05-14'})

        expect((inputs(view)[1]!.element as HTMLInputElement).disabled).toBe(true)
        expect((inputs(view)[0]!.element as HTMLInputElement).disabled).toBe(false)
        expect(view.text()).toContain('berechnet')
    })

    /** Nothing to count from is not a wrong number: it is no number. */
    it('says nothing where the question it counts from is unanswered', () => {
        const view = form({})

        expect((inputs(view)[1]!.element as HTMLInputElement).value).toBe('')
    })
})
