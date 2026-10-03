/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {DocumentLanguage} from '@/api/generated/schema'
import {compileDateFormat, DATE_TOKENS, exampleDate, printDate} from './dateFormatPattern'

function printed(pattern: string, language: DocumentLanguage = DocumentLanguage.DE): string {
    const compiled = compileDateFormat(pattern)
    if (!compiled.ok) throw new Error(compiled.problem)
    return printDate(compiled.parts, exampleDate(), language)
}

describe('an own date format', () => {
    it('prints every token on the example day, the words in the language of the template', () => {
        expect(DATE_TOKENS.map(token => `${token.written}=${printed(token.written)}`)).toEqual([
            'T=3', 'TT=03', 'TTT=Sa.', 'TTTT=Samstag', 'M=10', 'MM=10', 'MMM=Okt.', 'MMMM=Oktober', 'JJ=26', 'JJJJ=2026',
            'h=18', 'hh=18', 'mm=30',
        ])
        expect(printed('TTT, T. MMM JJJJ', DocumentLanguage.EN)).toBe('Sat, 3. Oct 2026')
    })

    it('keeps its separators as they stand', () => {
        expect(printed('TT.MM.JJ hh:mm / T-M')).toBe('03.10.26 18:30 / 3-10')
    })

    it('says whether it prints a time of day', () => {
        expect(compileDateFormat('TT.MM.JJJJ')).toMatchObject({ok: true, clock: false})
        expect(compileDateFormat('hh:mm')).toMatchObject({ok: true, clock: true})
    })

    it('refuses what the server refuses', () => {
        expect(compileDateFormat('  ')).toEqual({ok: false, problem: 'empty'})
        expect(compileDateFormat('T.'.repeat(21))).toEqual({ok: false, problem: 'tooLong'})
        expect(compileDateFormat('TT.MM.JJJJ Uhr')).toEqual({ok: false, problem: 'unknown', detail: 'U'})
        expect(compileDateFormat('TTTTT')).toEqual({ok: false, problem: 'unknown', detail: 'TTTTT'})
        expect(compileDateFormat('JJJ')).toEqual({ok: false, problem: 'unknown', detail: 'JJJ'})
        expect(compileDateFormat(' .-')).toEqual({ok: false, problem: 'noToken'})
        expect(compileDateFormat('T.'.repeat(20))).toMatchObject({ok: true})
    })
})
