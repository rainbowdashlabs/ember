/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import ChangeValueDiff from './ChangeValueDiff.vue'
import {FieldTypes} from '@/api/profileFields'
import type {ProfileFieldChange} from '@/api/profileFieldChanges'

/**
 * A change to a profile field as the history shows it: both sides written the way the field reads
 * on the profile, not the way it is stored.
 *
 * @vitest-environment happy-dom
 */
describe('ChangeValueDiff', () => {
    function show(oldValue: string | undefined, newValue: string | undefined, fieldType?: string | null) {
        const change: ProfileFieldChange = {
            id: 1, fieldId: 1, memberId: 1, changedBy: 1, requiresAcknowledgement: false,
            acknowledgements: [], oldValue, newValue, fieldType,
        }
        const wrapper = mount(ChangeValueDiff, {props: {change}, global: {stubs: {FontAwesomeIcon: true}}})
        return wrapper.findAll('[data-testid="change-side"]').map(side => side.text())
    }

    it('writes a changed date the way it is read', () => {
        expect(show('"2026-03-31"', '"2026-04-02"', FieldTypes.DATE)).toEqual(['31.03.2026', '02.04.2026'])
    })

    it('leaves the age off a changed birth date', () => {
        expect(show('"2019-11-03"', '"2019-11-04"', FieldTypes.BIRTH_DATE)).toEqual(['03.11.2019', '04.11.2019'])
    })

    it('leaves the state off a changed expiry date', () => {
        expect(show('"2020-01-31"', '"2020-02-29"', FieldTypes.EXPIRY_DATE)).toEqual(['31.01.2020', '29.02.2020'])
    })

    it('writes a missing side as a dash', () => {
        expect(show('null', '"2026-04-02"', FieldTypes.DATE)).toEqual(['–', '02.04.2026'])
        expect(show(undefined, '"Blau"', FieldTypes.TEXT)).toEqual(['–', 'Blau'])
    })

    it('leaves text alone', () => {
        expect(show('"2026-03-31"', '"Rot"', FieldTypes.TEXT)).toEqual(['2026-03-31', 'Rot'])
    })
})
