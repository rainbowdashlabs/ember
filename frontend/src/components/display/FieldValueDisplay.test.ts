/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import FieldValueDisplay from './FieldValueDisplay.vue'
import {FieldTypes} from '@/api/profileFields'

/**
 * An answer as a station reads it.
 *
 * <p>What is settled here is the age behind a birth date: that it is there by default, that a field
 * can say it should not be, and that turning it off leaves the date itself alone.
 */
describe('FieldValueDisplay', () => {
    function show(value: unknown, fieldType: string, config?: Record<string, unknown>) {
        return mount(FieldValueDisplay, {props: {value, fieldType, config}}).text()
    }

    it('writes a date the way it is read', () => {
        expect(show('2019-11-03', FieldTypes.DATE)).toBe('03.11.2019')
    })

    /** Every birth date carried its age before there was a choice, so silence keeps it. */
    it('puts the age behind a birth date by default', () => {
        expect(show('2019-11-03', FieldTypes.BIRTH_DATE)).toMatch(/^03\.11\.2019 \(\d+\)$/)
        expect(show('2019-11-03', FieldTypes.BIRTH_DATE, {})).toMatch(/^03\.11\.2019 \(\d+\)$/)
    })

    /** A station that asks the age as a question of its own does not want it stated twice. */
    it('leaves the age off where the field says so', () => {
        expect(show('2019-11-03', FieldTypes.BIRTH_DATE, {showAge: false})).toBe('03.11.2019')
    })

    it('says nothing where there is no answer', () => {
        expect(show('', FieldTypes.BIRTH_DATE)).toBe('–')
        expect(show(null, FieldTypes.DATE)).toBe('–')
    })

    /** A date nobody can parse is left as written, because showing nothing would lose it. */
    it('keeps a date it cannot read as it was written', () => {
        expect(show('irgendwann', FieldTypes.DATE)).toBe('irgendwann')
    })
})
