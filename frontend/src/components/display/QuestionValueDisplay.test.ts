/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import QuestionValueDisplay from './QuestionValueDisplay.vue'
import {FieldTypes} from '@/api/fieldTypes'

/**
 * An answer as a station reads it.
 *
 * <p>What is settled here is the age behind a birth date: that it is there by default, that a field
 * can say it should not be, and that turning it off leaves the date itself alone. And that a yes
 * reads as yes however it was stored, which is what screens that only knew one spelling got wrong.
 */
describe('QuestionValueDisplay', () => {
    function show(value: unknown, fieldType: string, config?: Record<string, unknown>) {
        return mount(QuestionValueDisplay, {props: {value, fieldType, config}}).text()
    }

    it('reads a yes stored as true, as the text true or as 1', () => {
        for (const value of [true, 'true', '1']) {
            expect(show(value, FieldTypes.BOOLEAN)).toBe('Ja')
        }
        expect(show('false', FieldTypes.BOOLEAN)).toBe('Nein')
        expect(show(false, FieldTypes.BOOLEAN)).toBe('Nein')
    })

    it('calls a yes what the field calls it', () => {
        const wrapper = mount(QuestionValueDisplay, {
            props: {value: true, fieldType: FieldTypes.BOOLEAN, yesLabel: 'Geprüft', noLabel: 'Offen'},
        })
        expect(wrapper.text()).toBe('Geprüft')
    })

    it('names the members an answer names', () => {
        const wrapper = mount(QuestionValueDisplay, {
            props: {value: '[1,2]', fieldType: FieldTypes.MEMBER_LIST, memberNames: new Map([[1, 'Anna'], [2, 'Ben']])},
        })
        expect(wrapper.text()).toBe('Anna, Ben')
    })

    it('writes a time the way a clock reads', () => {
        expect(show('09:30:00', FieldTypes.TIME)).toBe('09:30')
    })

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

    /** An expiry date says in words how close it is, so it reads without its colour. */
    describe('an expiry date', () => {
        function isoInDays(days: number): string {
            const day = new Date()
            day.setDate(day.getDate() + days)
            return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
        }

        function state(value: string, config?: Record<string, unknown>, bare = false) {
            const wrapper = mount(QuestionValueDisplay, {props: {value, fieldType: FieldTypes.EXPIRY_DATE, config, bare}})
            return wrapper.find('[data-testid="expiry-state"]')
        }

        it('stands alone while it is valid', () => {
            expect(state(isoInDays(200)).exists()).toBe(false)
        })

        it('says how many days are left once it runs out', () => {
            const badge = state(isoInDays(12))
            expect(badge.attributes('data-state')).toBe('EXPIRING')
            expect(badge.text()).toContain('12')
        })

        it('runs out as early as the field warns', () => {
            expect(state(isoInDays(60), {warnFromDays: 90}).attributes('data-state')).toBe('EXPIRING')
        })

        it('says it has expired once the last valid day has passed', () => {
            expect(state(isoInDays(-3)).attributes('data-state')).toBe('EXPIRED')
        })

        it('leaves the state off where it is written bare', () => {
            expect(state(isoInDays(-3), undefined, true).exists()).toBe(false)
        })
    })

    /** A date nobody can parse is left as written, because showing nothing would lose it. */
    it('keeps a date it cannot read as it was written', () => {
        expect(show('irgendwann', FieldTypes.DATE)).toBe('irgendwann')
    })
})
