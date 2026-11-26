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
        ['11-04-2024', '2024-04-11'],
        ['2024-04-11', '2024-04-11'],
        ['2024-4-1', '2024-04-01'],
        ['04/11/2024', '2024-04-11'],
        ['4/11/24', '2024-04-11'],
        [' 11.04.2024 ', '2024-04-11'],
    ])('automatically reads %s', (written, day) => {
        expect(read(written, 'auto')).toBe(day)
    })

    it.each([
        ['11.04.24', 'DD.MM.YYYY'],
        ['11-04-24', 'DD-MM-YYYY'],
        ['2024-04-11', 'YYYY-MM-DD'],
        ['04/11/24', 'MM/DD/YYYY'],
        ['11/04/24', 'DD/MM/YYYY'],
    ] as const)('reads %s in %s', (written, format) => {
        expect(read(written, format)).toBe('2024-04-11')
    })

    it.each([
        ['11.04.24', 'DD-MM-YYYY'],
        ['11-04-24', 'DD.MM.YYYY'],
        ['11/04/24', 'YYYY-MM-DD'],
        ['13/01/2024', 'MM/DD/YYYY'],
    ] as const)('does not read %s in %s', (written, format) => {
        expect(read(written, format)).toBeNull()
    })

    it.each(['', ' ', '31.02.2024', '11.04-24', 'morgen', '2024/04/11', '11.04.124', '13/01/2024'])(
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
    })
})

describe('detectDateFormat', () => {
    it.each([
        [['11.04.24', '1.12.2024'], 'DD.MM.YYYY'],
        [['11-04-24', '01-12-24'], 'DD-MM-YYYY'],
        [['2024-04-11', '2024-12-01'], 'YYYY-MM-DD'],
        [['04/11/24', '12/01/24'], 'MM/DD/YYYY'],
        [['04/11/24', '13/01/24'], 'DD/MM/YYYY'],
        [['04/11/24', '01/31/24'], 'MM/DD/YYYY'],
        [['11.04.24', 'morgen', ''], 'DD.MM.YYYY'],
        [['morgen', ''], 'auto'],
    ])('detects %j as %s', (column, format) => {
        expect(detectDateFormat(column)).toBe(format)
    })
})

describe('columnDateReader', () => {
    it('reads a cell the detected format cannot read on its own where the format is automatic', () => {
        const read = columnDateReader(['04.12.26', '2026-12-05'], 'auto')
        expect(read('04.12.26')).toBe('2026-12-04')
        expect(read('2026-12-05')).toBe('2026-12-05')
    })

    it('reads only the picked format where one is picked', () => {
        const read = columnDateReader(['04.12.26', '2026-12-05'], 'DD.MM.YYYY')
        expect(read('2026-12-05')).toBeNull()
    })

    it('reads a column of slashed days day first once one day passes the twelfth', () => {
        expect(columnDateReader(['01/04/27', '13/04/27'], 'auto')('01/04/27')).toBe('2027-04-01')
    })
})
