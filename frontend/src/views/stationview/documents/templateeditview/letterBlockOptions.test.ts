/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {CellContentType, FontOrigin, FontStyle} from '@/api/generated/schema'
import {LETTER_COLUMNS, LETTERHEAD_COLUMNS, letterBlockOptions} from './letterBlockOptions'

const catalogue = {
    placeholders: [],
    labels: new Map([['member.firstName', 'Vorname']]),
    legal: false,
    choices: {groups: [], tags: []},
    fonts: [{family: 'Hausschrift', origin: FontOrigin.INSTANCE, styles: [FontStyle.REGULAR], printsOnPdf: true, sample: 'v1'}],
}

describe('the block editor of a letter', () => {
    it('offers texts, pictures, lines and gaps in four letterhead and three body columns, the logo and a visibility', () => {
        const options = letterBlockOptions(catalogue, false)

        expect(options.allowedKinds).toEqual([
            CellContentType.MARKDOWN,
            CellContentType.IMAGE,
            CellContentType.DIVIDER,
            CellContentType.SPACER,
        ])
        expect(options.maxColumns).toBe(LETTERHEAD_COLUMNS)
        expect(letterBlockOptions(catalogue, true).maxColumns).toBe(LETTER_COLUMNS)
        expect(options.stationLogo).toBe(true)
        expect(options.restrictable).toBe(catalogue.choices)
        expect(options.guardianCondition).toBe(true)
        expect(options.columnLines).toBe(true)
        expect(options.markdownTools).toBeDefined()
    })

    it('offers signature lines in the body only', () => {
        expect(letterBlockOptions(catalogue, true).allowedKinds).toContain(CellContentType.SIGNATURE)
        expect(letterBlockOptions(catalogue, false).allowedKinds).not.toContain(CellContentType.SIGNATURE)
    })

    it('offers the families the template reaches for selected words', () => {
        expect(letterBlockOptions(catalogue, false).textFonts).toBe(catalogue.fonts)
    })

    it('shows placeholders as chips with their labels', () => {
        const prepared = letterBlockOptions(catalogue, false).tokens?.prepare('Hallo {{member.firstName}}')

        expect(prepared).toContain('>Vorname</span>')
    })
})
