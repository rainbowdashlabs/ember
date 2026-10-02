/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import ProfileFieldsLayout, {type LaidOutField} from './ProfileFieldsLayout.vue'
import {FieldTypes} from '@/api/fieldTypes'

/**
 * The form a member's answers are given on.
 *
 * <p>What is settled here is the question nobody answers: an age is worked out from a date, so the
 * form shows the number it comes to and does not offer it to be typed over. What was once typed
 * into it, before it began working itself out, is not what it shows.
 */
describe('ProfileFieldsLayout', () => {
    const birthDate = question(10, 'Geburtsdatum', FieldTypes.BIRTH_DATE)
    const age = question(12, 'Alter', FieldTypes.AGE, {sourceFieldId: 10, ageMode: 'now'})

    function question(id: number, name: string, fieldType: string, config: LaidOutField['config'] = {}): LaidOutField {
        return {id, name, fieldType, config, required: false, width: null}
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

    /** The member sees their own date running out before any reminder tells them. */
    it('marks an expiry date that has passed beside its label', () => {
        const firstAid = question(20, 'Erste Hilfe gültig bis', FieldTypes.EXPIRY_DATE)
        const view = mount(ProfileFieldsLayout, {
            props: {fields: [firstAid], getValue: () => '2020-01-31'},
        })

        expect(view.find('[data-testid="expiry-state"]').attributes('data-state')).toBe('EXPIRED')
    })

    /** Nothing to count from is not a wrong number: it is no number. */
    it('says nothing where the question it counts from is unanswered', () => {
        const view = form({})

        expect((inputs(view)[1]!.element as HTMLInputElement).value).toBe('')
    })
})
