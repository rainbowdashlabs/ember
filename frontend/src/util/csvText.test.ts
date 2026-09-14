/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {readCsvText} from './csvText'

function fileOf(bytes: number[]): File {
    return new File([new Uint8Array(bytes)], 'members.csv', {type: 'text/csv'})
}

function utf8(text: string): File {
    return fileOf([...new TextEncoder().encode(text)])
}

describe('readCsvText', () => {
    it('reads a plain UTF-8 export', async () => {
        expect(await readCsvText(utf8('first;last\nAnna;Smith\n'))).toBe('first;last\nAnna;Smith\n')
    })

    it('keeps the umlauts of a UTF-8 export', async () => {
        expect(await readCsvText(utf8('first;last\nJörg;Möckel\n'))).toBe('first;last\nJörg;Möckel\n')
    })

    /**
     * The reported case. Windows-1252 writes an o umlaut as the single byte 0xF6, which UTF-8 has
     * no reading for, and decoding it as UTF-8 replaces the name with a character that cannot be
     * turned back.
     */
    it('reads an umlaut a spreadsheet wrote as Windows-1252', async () => {
        const windows1252 = [0x4d, 0xf6, 0x63, 0x6b, 0x65, 0x6c]

        expect(await readCsvText(fileOf(windows1252))).toBe('Möckel')
    })

    it('reads the Windows-1252 characters that sit where UTF-8 puts continuation bytes', async () => {
        const windows1252 = [0x80, 0x93, 0x9f]

        expect(await readCsvText(fileOf(windows1252))).toBe('€“Ÿ')
    })

    it('strips the byte order mark a spreadsheet writes in front of UTF-8', async () => {
        const withMark = fileOf([0xef, 0xbb, 0xbf, ...new TextEncoder().encode('first;last')])

        expect(await readCsvText(withMark)).toBe('first;last')
    })

    it('never loses a character to the replacement character', async () => {
        const windows1252 = [0x53, 0x63, 0x68, 0x72, 0xf6, 0x64, 0x74, 0x65, 0x72]

        expect(await readCsvText(fileOf(windows1252))).not.toContain('�')
    })

    it('reads an empty file as empty text', async () => {
        expect(await readCsvText(fileOf([]))).toBe('')
    })
})
