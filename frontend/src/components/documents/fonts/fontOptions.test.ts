/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {FontOrigin, FontOutline, FontStyle, type DocumentFontView, type FontFamilyOption} from '@/api/generated/schema'
import {choiceValue, familyChoices, familyOf, groupByFamily, previewStyle, printedStyle} from './fontOptions'

function family(name: string, styles: FontStyle[] = [FontStyle.REGULAR], printsOnPdf = true): FontFamilyOption {
    return {family: name, origin: FontOrigin.STATION, styles, printsOnPdf}
}

function font(id: number, name: string, style: FontStyle): DocumentFontView {
    return {id, family: name, style, fileName: `${name}.ttf`, outline: FontOutline.TRUETYPE, sizeBytes: 1, uploadedAt: '2026-10-03T00:00:00Z'}
}

describe('familyChoices', () => {
    const reachable = [family('Hausschrift'), family('Nur Brief', [FontStyle.REGULAR], false)]

    it('offers the default font first and every family reached', () => {
        expect(familyChoices(reachable, null).map(choice => choice.value)).toEqual(['', 'Hausschrift', 'Nur Brief'])
    })

    it('leaves out what a field on a PDF cannot print', () => {
        expect(familyChoices(reachable, null, true).map(choice => choice.value)).toEqual(['', 'Hausschrift'])
    })

    it('keeps a family the template names but no longer reaches, marked missing', () => {
        const choices = familyChoices(reachable, 'Gelöscht')
        expect(choices.at(-1)).toEqual({value: 'Gelöscht', family: null, missing: true})
        expect(choiceValue(choices, 'Gelöscht')).toBe('Gelöscht')
    })

    it('marks a family missing on a PDF field that only a letter can print', () => {
        expect(familyChoices(reachable, 'Nur Brief', true).at(-1)?.missing).toBe(true)
    })

    it('finds a reached family whatever its case, without a missing twin', () => {
        const choices = familyChoices(reachable, 'hausschrift')
        expect(choices.some(choice => choice.missing)).toBe(false)
        expect(choiceValue(choices, 'hausschrift')).toBe('Hausschrift')
        expect(choiceValue(choices, null)).toBe('')
    })
})

describe('familyOf', () => {
    it('stores the default font as no family', () => {
        expect(familyOf('')).toBeNull()
        expect(familyOf('  ')).toBeNull()
        expect(familyOf(' Hausschrift ')).toBe('Hausschrift')
    })
})

describe('printedStyle', () => {
    it('falls back to regular where the family lacks the style, as the document does', () => {
        expect(printedStyle(family('Hausschrift', [FontStyle.REGULAR, FontStyle.BOLD]), FontStyle.BOLD)).toBe(FontStyle.BOLD)
        expect(printedStyle(family('Hausschrift'), FontStyle.ITALIC)).toBe(FontStyle.REGULAR)
        expect(printedStyle(null, FontStyle.BOLD_ITALIC)).toBe(FontStyle.BOLD_ITALIC)
    })

    it('previews a style with the weight and slant of the fallback', () => {
        expect(previewStyle(FontStyle.BOLD_ITALIC)).toEqual({fontWeight: '700', fontStyle: 'italic'})
        expect(previewStyle(FontStyle.REGULAR)).toEqual({fontWeight: '400', fontStyle: 'normal'})
    })
})

describe('groupByFamily', () => {
    it('groups the files of a family in style order, families by name', () => {
        const groups = groupByFamily([
            font(1, 'Zierde', FontStyle.REGULAR),
            font(2, 'Hausschrift', FontStyle.ITALIC),
            font(3, 'hausschrift', FontStyle.REGULAR),
        ])
        expect(groups.map(group => group.family)).toEqual(['Hausschrift', 'Zierde'])
        expect(groups[0]?.fonts.map(entry => entry.id)).toEqual([3, 2])
    })
})
