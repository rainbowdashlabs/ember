/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {afterEach, beforeEach, describe, expect, it} from 'vitest'
import {
    dateToInstant,
    formatDate,
    formatDateLong,
    formatDateTime,
    formatDayMonth,
    formatTime,
    formatWeekdayDate,
    instantToDate,
    instantToLocalInput,
    todayIsoDate,
    toIsoDate,
} from './format'

const originalZone = process.env.TZ

function readingFrom(zone: string) {
    process.env.TZ = zone
}

beforeEach(() => readingFrom('Europe/Berlin'))
afterEach(() => {
    process.env.TZ = originalZone
})

/**
 * A calendar date and an instant look interchangeable and are not. Handed a bare calendar date,
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

describe('the day a value names', () => {
    it('writes a moment as the day it falls on where the reader stands', () => {
        expect(formatDate('2026-10-12T18:00:00Z')).toBe('12.10.2026')
    })

    /**
     * A calendar date is that date everywhere. Read as a moment it is midnight in London, which is
     * still the day before anywhere west of it, and a birthday shown a day early is the visible end
     * of that.
     */
    it('leaves a plain calendar date alone wherever it is read', () => {
        expect(formatDate('2026-10-12')).toBe('12.10.2026')
        readingFrom('America/New_York')
        expect(formatDate('2026-10-12')).toBe('12.10.2026')
        readingFrom('Pacific/Auckland')
        expect(formatDate('2026-10-12')).toBe('12.10.2026')
    })

    it('drops the year where a card has no room for it', () => {
        expect(formatDayMonth('2026-10-12')).toBe('12.10.')
        expect(formatDayMonth(null)).toBe('')
    })

    it('says nothing about a value that is not a date', () => {
        expect(formatDate('')).toBe('')
        expect(formatDate(null)).toBe('')
        expect(formatDate('irgendwann')).toBe('')
    })

    it('puts the weekday in front where a single day is named', () => {
        expect(formatWeekdayDate('2026-10-12')).toBe('Montag, 12.10.2026')
        expect(formatWeekdayDate('2026-10-12', 'short')).toBe('Mo., 12.10.2026')
    })

    it('writes a long date for editorial pages', () => {
        expect(formatDateLong('2026-07-27')).toBe('27. Juli 2026')
    })
})

describe('the clock a value shows', () => {
    it('reads a stored moment on the clock of whoever is looking', () => {
        expect(formatTime('2026-10-12T18:00:00Z')).toBe('20:00')
        readingFrom('UTC')
        expect(formatTime('2026-10-12T18:00:00Z')).toBe('18:00')
    })

    it('shortens a plain clock reading that carries no zone at all', () => {
        expect(formatTime('18:00:00')).toBe('18:00')
        expect(formatTime('9:05')).toBe('09:05')
    })

    it('says nothing where there is no time', () => {
        expect(formatTime('')).toBe('')
        expect(formatTime(null)).toBe('')
        expect(formatTime('abends')).toBe('')
    })

    it('writes the day and the clock together', () => {
        expect(formatDateTime('2026-10-12T18:00:00Z')).toBe('12.10.2026, 20:00')
    })
})

/**
 * The evening appointment that has already turned over in London. Everything that asked the stored
 * string for its day put such an appointment on the day before, which is a day out on the calendar,
 * in the list of who signed up, and in every lookup keyed by the date.
 */
describe('an appointment late in the evening', () => {
    const lateInTheEvening = '2026-10-12T22:30:00Z'

    it('falls on the day the reader is living through, not the one in London', () => {
        expect(toIsoDate(new Date(lateInTheEvening))).toBe('2026-10-13')
        expect(formatDate(lateInTheEvening)).toBe('13.10.2026')
        expect(formatWeekdayDate(lateInTheEvening)).toBe('Dienstag, 13.10.2026')
        expect(formatTime(lateInTheEvening)).toBe('00:30')
    })

    it('and on the day before for a reader who is still on the twelfth', () => {
        readingFrom('UTC')
        expect(toIsoDate(new Date(lateInTheEvening))).toBe('2026-10-12')
        expect(formatTime(lateInTheEvening)).toBe('22:30')
    })

    it('goes into an editable field on the reader\'s own clock', () => {
        expect(instantToLocalInput(lateInTheEvening)).toBe('2026-10-13T00:30')
        expect(instantToLocalInput(null)).toBe('')
        expect(instantToLocalInput('irgendwann')).toBe('')
    })

    it('survives the round trip an editable field makes of it', () => {
        const backOut = new Date(instantToLocalInput(lateInTheEvening)).toISOString()
        expect(backOut).toBe('2026-10-12T22:30:00.000Z')
    })
})

describe('today', () => {
    it('is the day the reader is living through', () => {
        const now = new Date()
        expect(todayIsoDate()).toBe(
            `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`)
    })
})
