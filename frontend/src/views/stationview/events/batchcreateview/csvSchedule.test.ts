/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {localInputToInstant} from '@/util/format'
import {
    CsvTarget,
    csvToBatchRows,
    guessColumnMapping,
    parseCsvTime,
    readCsvTable,
} from './csvSchedule'

const ADVENT = '﻿ ImprDatum,Von,Bis,Titel\n'
    + '04.12.26,16:30,19:00,Adventszauber Freitag 1\n'
    + '04.12.26,19:00,21:30,Adventszauber Freitag 2\n'
    + '05.12.26,16:30,19:00,Adventszauber Samstag 1\n'
    + '05.12.26,19:00,21:30,Adventszauber Samstag 2\n'

function imported(text: string) {
    const table = readCsvTable(text)
    const mapping = guessColumnMapping(table, [])
    return csvToBatchRows(table, {
        mapping,
        dateFormat: 'auto',
        fallbackStartTime: '09:00',
        fallbackEndTime: '17:00',
    })
}

describe('readCsvTable', () => {
    it('drops the byte order mark and the space in front of a header name', () => {
        expect(readCsvTable(ADVENT).columns).toEqual(['ImprDatum', 'Von', 'Bis', 'Titel'])
    })

    it('drops lines of nothing but separators, as spreadsheets write them below the data', () => {
        expect(readCsvTable('Datum;Von\n01.01.2026;10:00\n;;\n ; \n').lines).toEqual([{line: 2, cells: ['01.01.2026', '10:00']}])
        expect(readCsvTable('Datum,Von\n01.01.2026,10:00\n,,,\n').lines).toHaveLength(1)
    })

    it('keeps the line number of the file for every line', () => {
        expect(readCsvTable('Datum\r\n\r\n01.01.2026\r\n').lines).toEqual([{line: 3, cells: ['01.01.2026']}])
    })
})

describe('guessColumnMapping', () => {
    it('recognises a column whose name contains Datum as the date', () => {
        expect(guessColumnMapping(readCsvTable(ADVENT), [])).toEqual({
            ImprDatum: CsvTarget.DATE,
            Von: CsvTarget.START_TIME,
            Bis: CsvTarget.END_TIME,
            Titel: CsvTarget.NAME,
        })
    })

    it('takes the first column holding only dates where no column is named like one', () => {
        const table = readCsvTable('Wann,Was\n04.12.26,Probe\n2026-12-05,Probe\n')
        expect(guessColumnMapping(table, [])).toEqual({Wann: CsvTarget.DATE})
    })

    it('takes the plain date column and only that one where several look like dates', () => {
        const table = readCsvTable('Erstelldatum;Datum;Von;Bis\n01.11.2026;04.12.2026;16:30;19:00\n')
        expect(guessColumnMapping(table, [])).toEqual({
            Datum: CsvTarget.DATE,
            Von: CsvTarget.START_TIME,
            Bis: CsvTarget.END_TIME,
        })
    })

    it('takes only the first of several columns that merely say date', () => {
        const table = readCsvTable('Erstelldatum;Termindatum\n01.11.2026;04.12.2026\n')
        expect(guessColumnMapping(table, [])).toEqual({Erstelldatum: CsvTarget.DATE})
    })

    it('leaves a column named like a field of the batch to that field', () => {
        const table = readCsvTable('ImprDatum,Abgabedatum\n04.12.26,01.12.26\n')
        expect(guessColumnMapping(table, ['Abgabedatum']))
            .toEqual({ImprDatum: CsvTarget.DATE, Abgabedatum: 'Abgabedatum'})
    })

    it('does not read a word that merely contains date as a date column', () => {
        const table = readCsvTable('Update,Validated,Candidate,Start Date\nja,nein,Anna,04.12.2026\n')
        expect(guessColumnMapping(table, [])).toEqual({'Start Date': CsvTarget.DATE})
    })

    it('maps a column to the field of the same name', () => {
        expect(guessColumnMapping(readCsvTable('datum,Fahrer\n01.01.2026,Anna\n'), ['fahrer']))
            .toEqual({datum: CsvTarget.DATE, Fahrer: 'fahrer'})
    })
})

describe('parseCsvTime', () => {
    it('reads the times a spreadsheet writes', () => {
        expect(parseCsvTime('16:30')).toBe('16:30')
        expect(parseCsvTime('9:05')).toBe('09:05')
        expect(parseCsvTime('19.00')).toBe('19:00')
        expect(parseCsvTime('19:00:00')).toBe('19:00')
    })

    it('refuses what no clock can show', () => {
        expect(parseCsvTime('25:00')).toBeNull()
        expect(parseCsvTime('abends')).toBeNull()
    })
})

describe('csvToBatchRows', () => {
    it('imports a spreadsheet export on the dates and times it names', () => {
        const result = imported(ADVENT)

        expect(result.problems).toEqual([])
        expect(result.rows.map(row => [row.name, row.startTime, row.endTime])).toEqual([
            ['Adventszauber Freitag 1', localInputToInstant('2026-12-04T16:30'), localInputToInstant('2026-12-04T19:00')],
            ['Adventszauber Freitag 2', localInputToInstant('2026-12-04T19:00'), localInputToInstant('2026-12-04T21:30')],
            ['Adventszauber Samstag 1', localInputToInstant('2026-12-05T16:30'), localInputToInstant('2026-12-05T19:00')],
            ['Adventszauber Samstag 2', localInputToInstant('2026-12-05T19:00'), localInputToInstant('2026-12-05T21:30')],
        ])
    })

    it('names a line whose date or time cannot be read instead of placing it at the moment of the import', () => {
        const result = imported('Datum,Von,Bis\n04.12.26,16:30,19:00\nsoon,16:30,19:00\n05.12.26,abends,19:00\n')

        expect(result.rows).toHaveLength(1)
        expect(result.problems).toEqual([
            {line: 3, reason: 'date', value: 'soon'},
            {line: 4, reason: 'startTime', value: 'abends'},
        ])
    })

    it('refuses a line that ends at or before its start, the shape of an appointment placed at one moment', () => {
        const result = imported('Datum,Von,Bis\n04.12.26,18:22,18:22\n04.12.26,19:00,16:30\n')

        expect(result.rows).toEqual([])
        expect(result.problems).toEqual([
            {line: 2, reason: 'order', value: '18:22 - 18:22'},
            {line: 3, reason: 'order', value: '19:00 - 16:30'},
        ])
    })

    it('imports a spreadsheet export with separator lines at the end', () => {
        expect(imported(`${ADVENT},,,\n,,,\n`).rows).toHaveLength(4)
    })

    it('names every line where no column is mapped to the date', () => {
        const table = readCsvTable('Von,Bis\n16:30,19:00\n')
        const result = csvToBatchRows(table, {
            mapping: {Von: CsvTarget.START_TIME, Bis: CsvTarget.END_TIME},
            dateFormat: 'auto',
            fallbackStartTime: '09:00',
            fallbackEndTime: '17:00',
        })

        expect(result.rows).toEqual([])
        expect(result.problems).toEqual([{line: 2, reason: 'date', value: ''}])
    })

    it('takes the fallback times where no time column is mapped', () => {
        const result = imported('Datum\n04.12.2026\n')

        expect(result.rows[0]?.startTime).toBe(localInputToInstant('2026-12-04T09:00'))
        expect(result.rows[0]?.endTime).toBe(localInputToInstant('2026-12-04T17:00'))
    })
})

describe('csvToBatchRows with an automatic date format', () => {
    it('reads a column of slashed days day first once one day passes the twelfth', () => {
        const result = imported('Datum;Von;Bis\n01/04/27;18:00;20:00\n13/04/27;18:00;20:00\n')
        expect(result.problems).toEqual([])
        expect(result.rows.map(row => row.startTime)).toEqual([
            localInputToInstant('2027-04-01T18:00'),
            localInputToInstant('2027-04-13T18:00'),
        ])
    })
})
