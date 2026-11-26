/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {columnDateReader, detectDateFormat, parseCsvDate, type CsvDateFormat} from './csvDate'

/**
 * The cases match those of the server's reader in `WrittenDayTest.java`, so both read a file alike.
 */
const THIS_YEAR = 2026

function read(written: string, format: CsvDateFormat): string | null {
    return parseCsvDate(written, format, 'century', THIS_YEAR)
}

describe('parseCsvDate', () => {
    it.each([
        ['11.04.2024', '2024-04-11'],
        ['1.4.2024', '2024-04-01'],
        ['11.04.24', '2024-04-11'],
        ['11-04-24', '2024-04-11'],
        ['11/04/2024', '2024-04-11'],
        ['2024-04-11', '2024-04-11'],
        ['2024-4-1', '2024-04-01'],
        ['2024/04/11', '2024-04-11'],
        ['04/13/2024', '2024-04-13'],
        ['03-16-60', '2060-03-16'],
        [' 11.04.2024 ', '2024-04-11'],
    ])('automatically reads %s', (written, day) => {
        expect(read(written, 'auto')).toBe(day)
    })

    it.each([
        ['11.04.24', 'day-month-year'],
        ['11-04-2024', 'day-month-year'],
        ['11/04/24', 'day-month-year'],
        ['04.11.24', 'month-day-year'],
        ['04-11-2024', 'month-day-year'],
        ['04/11/24', 'month-day-year'],
        ['2024.04.11', 'year-month-day'],
        ['2024-04-11', 'year-month-day'],
        ['2024/4/11', 'year-month-day'],
    ] as const)('reads %s in %s', (written, format) => {
        expect(read(written, format)).toBe('2024-04-11')
    })

    it.each([
        ['03-16-60', 'day-month-year'],
        ['2024-04-11', 'day-month-year'],
        ['11.04.2024', 'year-month-day'],
        ['24/04/11', 'year-month-day'],
    ] as const)('does not read %s in %s', (written, format) => {
        expect(read(written, format)).toBeNull()
    })

    it.each(['', ' ', '31.02.2024', '11.04-24', 'morgen', '11.04.124', '111.04.2024', '40/40/2024'])(
        'reads no day from "%s"', written => {
            expect(read(written, 'auto')).toBeNull()
        })

    it('keeps a two-digit appointment year in this century', () => {
        expect(read('01.01.30', 'auto')).toBe('2030-01-01')
        expect(read('03.05.78', 'auto')).toBe('2078-05-03')
    })

    it('never puts a two-digit birth year in the future', () => {
        expect(parseCsvDate('03.05.78', 'auto', 'past', THIS_YEAR)).toBe('1978-05-03')
        expect(parseCsvDate('01.01.26', 'auto', 'past', THIS_YEAR)).toBe('2026-01-01')
        expect(parseCsvDate('01.01.27', 'auto', 'past', THIS_YEAR)).toBe('1927-01-01')
        expect(parseCsvDate('01.01.00', 'auto', 'past', THIS_YEAR)).toBe('2000-01-01')
        expect(parseCsvDate('03-16-60', 'auto', 'past', THIS_YEAR)).toBe('1960-03-16')
    })
})

describe('detectDateFormat', () => {
    it.each([
        [['11.04.24', '1.12.2024'], 'day-month-year'],
        [['11-04-24', '01/12/24'], 'day-month-year'],
        [['04/11/24', '12/01/24'], 'day-month-year'],
        [['05-04-60', '03-16-60'], 'month-day-year'],
        [['04/11/24', '01/31/24'], 'month-day-year'],
        [['2024-04-11', '2024-12-01'], 'year-month-day'],
        [['60-03-16', '71.12.01'], 'year-month-day'],
        [['11.04.24', 'morgen', ''], 'day-month-year'],
        [['morgen', ''], 'auto'],
    ])('detects %j as %s', (column, format) => {
        expect(detectDateFormat(column)).toBe(format)
    })
})

describe('columnDateReader', () => {
    it('reads a cell the detected order cannot read on its own where the order is automatic', () => {
        const readColumn = columnDateReader(['04.12.26', '2026-12-05'], 'auto')
        expect(readColumn('04.12.26')).toBe('2026-12-04')
        expect(readColumn('2026-12-05')).toBe('2026-12-05')
    })

    it('reads only the picked order where one is picked', () => {
        expect(columnDateReader(['04.12.26'], 'day-month-year')('2026-12-05')).toBeNull()
    })

    it('reads a month-first column once one day passes the twelfth', () => {
        expect(columnDateReader(['05-04-60', '03-16-60'], 'auto', 'past')('05-04-60')).toBe('1960-05-04')
    })
})
