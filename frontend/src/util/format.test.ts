/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {dateToInstant, instantToDate} from './format'

/**
 * A date field and a timestamp field look interchangeable and are not. Handed a bare calendar date,
 * an endpoint expecting an instant refuses the whole request, so the due date a person typed never
 * arrives and nothing on the screen says so. These two carry the value across that seam.
 */
describe('a calendar date and an instant', () => {
    it('turns what a date field holds into a full instant', () => {
        expect(dateToInstant('2026-03-17')).toBe('2026-03-17T00:00:00.000Z')
    })

    it('has nothing to send when the field is empty', () => {
        expect(dateToInstant('')).toBeNull()
        expect(dateToInstant(null)).toBeNull()
        expect(dateToInstant(undefined)).toBeNull()
    })

    it('refuses to invent an instant out of something that is not a date', () => {
        expect(dateToInstant('irgendwann')).toBeNull()
    })

    it('puts a stored instant back into a date field', () => {
        expect(instantToDate('2026-03-17T00:00:00.000Z')).toBe('2026-03-17')
    })

    it('leaves the date field empty when there is nothing stored', () => {
        expect(instantToDate(null)).toBe('')
        expect(instantToDate('kein Datum')).toBe('')
    })

    it('survives the round trip a form makes of it', () => {
        expect(instantToDate(dateToInstant('2026-12-24'))).toBe('2026-12-24')
    })
})
