/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {CellContentType, FontOrigin, FontStyle, LetterPart} from '@/api/generated/schema'
import {letterBlockOptions} from './letterBlockOptions'

const LETTERHEAD_KINDS = [CellContentType.MARKDOWN, CellContentType.IMAGE, CellContentType.DIVIDER, CellContentType.SPACER]

const catalogue = {
    placeholders: [],
    labels: new Map([['member.firstName', 'Vorname']]),
    legal: false,
    choices: {groups: [], tags: []},
    fonts: [{family: 'Hausschrift', origin: FontOrigin.INSTANCE, styles: [FontStyle.REGULAR], printsOnPdf: true, sample: 'v1', editorVersion: 'e1'}],
    parts: [
        {part: LetterPart.LETTERHEAD, maxColumns: 4, kinds: LETTERHEAD_KINDS},
        {part: LetterPart.BODY, maxColumns: 3, kinds: [...LETTERHEAD_KINDS, CellContentType.SIGNATURE]},
    ],
}

describe('the block editor of a letter', () => {
    it('offers the blocks and columns the server names for the part, the logo and a visibility', () => {
        const options = letterBlockOptions(catalogue, LetterPart.LETTERHEAD)

        expect(options.allowedKinds).toEqual(LETTERHEAD_KINDS)
        expect(options.maxColumns).toBe(4)
        expect(letterBlockOptions(catalogue, LetterPart.BODY).maxColumns).toBe(3)
        expect(options.stationLogo).toBe(true)
        expect(options.restrictable).toBe(catalogue.choices)
        expect(options.guardianCondition).toBe(true)
        expect(options.columnLines).toBe(true)
        expect(options.verticalDivider).toBe(true)
        expect(options.markdownTools).toBeDefined()
    })

    it('offers signature lines where the server names them', () => {
        expect(letterBlockOptions(catalogue, LetterPart.BODY).allowedKinds).toContain(CellContentType.SIGNATURE)
        expect(letterBlockOptions(catalogue, LetterPart.LETTERHEAD).allowedKinds).not.toContain(CellContentType.SIGNATURE)
    })

    it('offers no block until the server has named the parts', () => {
        const options = letterBlockOptions({...catalogue, parts: []}, LetterPart.BODY)

        expect(options.allowedKinds).toEqual([])
        expect(options.maxColumns).toBe(1)
    })

    it('offers the families the template reaches for selected words', () => {
        expect(letterBlockOptions(catalogue, LetterPart.LETTERHEAD).textFonts).toBe(catalogue.fonts)
    })

    it('shows placeholders as chips with their labels', () => {
        const prepared = letterBlockOptions(catalogue, LetterPart.LETTERHEAD).tokens?.prepare('Hallo {{member.firstName}}')

        expect(prepared).toContain('>Vorname</span>')
    })
})
