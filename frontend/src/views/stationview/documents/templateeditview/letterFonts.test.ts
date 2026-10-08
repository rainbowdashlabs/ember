/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {CellContentType, DocumentTemplateKind, PdfFieldKind, TextAlign} from '@/api/generated/schema'
import {emptyDraft} from './templateDraft'
import {letterFamilies, templateFamilies} from './letterFonts'

function row(content: string, nested?: string) {
    const config = nested ? {cells: [{contentType: CellContentType.MARKDOWN, content: nested}]} : {}
    return {
        id: 1,
        sortOrder: 0,
        cells: [{id: 1, sortOrder: 0, widthPercent: 100, contentType: CellContentType.MARKDOWN, content, config}],
    }
}

/** The families a letter's editor loads: those of its page and those words are set in, however deep. */
describe('letterFamilies', () => {
    it('names the page fonts and the families of words in every part', () => {
        const draft = emptyDraft(DocumentTemplateKind.LETTER)
        draft.page = {...draft.page, bodyFont: 'Brotschrift', headerFont: null}
        draft.header = [row('<span data-font="Kopf">K</span>')]
        draft.body = [row('Text', '<span data-font="Tief">T</span> <span data-font="Kopf">K</span>')]

        expect(letterFamilies(draft)).toEqual(['Brotschrift', null, null, 'Kopf', 'Tief'])
    })

    it('names none for a PDF template', () => {
        expect(letterFamilies(emptyDraft(DocumentTemplateKind.PDF))).toEqual([])
    })
})

describe('templateFamilies', () => {
    it('names the fonts of a PDF template\'s text fields once each, and none of its other fields', () => {
        const draft = emptyDraft(DocumentTemplateKind.PDF)
        const text = {
            kind: PdfFieldKind.TEXT, rect: {page: 1, x: 0, y: 0, width: 10, height: 10}, text: 'A', fontSize: 10,
            fontFamily: 'Hausschrift', fontStyle: null, align: TextAlign.LEFT, wrap: false, role: null,
            withoutLine: false, printText: false, statement: null, required: false, maxLength: null,
        }
        draft.fields = [text, {...text}, {...text, fontFamily: null}, {...text, kind: PdfFieldKind.CHECK, fontFamily: 'Kreuz'}]

        expect(templateFamilies(draft)).toEqual(['Hausschrift', null])
    })

    it('names a letter\'s families for a letter', () => {
        const draft = emptyDraft(DocumentTemplateKind.LETTER)
        expect(templateFamilies(draft)).toEqual(letterFamilies(draft))
    })
})
