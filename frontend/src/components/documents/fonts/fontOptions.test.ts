/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {FontOrigin, FontOutline, FontStyle, type DocumentFontView, type FontFamilyOption} from '@/api/generated/schema'
import {choiceValue, familyChoices, familyOf, fontEntries, groupByFamily, printedStyle, reachedFamily} from './fontOptions'

function family(name: string, styles: FontStyle[] = [FontStyle.REGULAR], printsOnPdf = true): FontFamilyOption {
    return {family: name, origin: FontOrigin.STATION, styles, printsOnPdf, sample: 'v1', editorVersion: 'e1'}
}

function font(id: number, name: string, style: FontStyle): DocumentFontView {
    return {id, family: name, style, fileName: `${name}.ttf`, outline: FontOutline.TRUETYPE, sizeBytes: 1, uploadedAt: '2026-10-03T00:00:00Z', web: null}
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

})

describe('reachedFamily', () => {
    it('finds the family a template names whatever its case, and nothing for the default font or one gone', () => {
        const reachable = [family('Hausschrift')]
        expect(reachedFamily(reachable, 'HAUSSCHRIFT')).toBe(reachable[0])
        expect(reachedFamily(reachable, null)).toBeNull()
        expect(reachedFamily(reachable, 'Gelöscht')).toBeNull()
    })
})

describe('fontEntries', () => {
    const reachable = [family('Hausschrift'), {...family('Liberation Serif'), origin: FontOrigin.BUILT_IN, sample: 'v2'}]

    it('shows every family with its origin and its sample, the default font by the family it is', () => {
        expect(fontEntries(familyChoices(reachable, null), {family: 'Berlin Type Office'})).toEqual([
            {value: '', name: 'Berlin Type Office', origin: 'DEFAULT', sample: {family: null, version: 'Berlin Type Office'}, missing: false},
            {value: 'Hausschrift', name: 'Hausschrift', origin: FontOrigin.STATION, sample: {family: 'Hausschrift', version: 'v1'}, missing: false},
            {value: 'Liberation Serif', name: 'Liberation Serif', origin: FontOrigin.BUILT_IN, sample: {family: 'Liberation Serif', version: 'v2'}, missing: false},
        ])
    })

    it('names a default it cannot draw and a family no longer reached without a sample', () => {
        const entries = fontEntries(familyChoices(reachable, 'Gelöscht'), {name: 'Schrift der Vorlage'})
        expect(entries[0]).toEqual({value: '', name: 'Schrift der Vorlage', origin: null, sample: null, missing: false})
        expect(entries.at(-1)).toEqual({value: 'Gelöscht', name: 'Gelöscht', origin: null, sample: null, missing: true})
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
