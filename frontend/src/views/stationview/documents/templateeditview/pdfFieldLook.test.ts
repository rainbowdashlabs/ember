/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {FontStyle, PdfFieldKind, TextAlign, type PdfField} from '@/api/generated/schema'
import {fieldTextLook} from './pdfFieldLook'

function field(overrides: Partial<PdfField> = {}): PdfField {
    return {
        kind: PdfFieldKind.TEXT,
        rect: {page: 1, x: 10, y: 10, width: 100, height: 20},
        text: '{{member.firstName}}',
        fontSize: 12,
        fontFamily: null,
        fontStyle: null,
        align: TextAlign.LEFT,
        wrap: false,
        role: null,
        withoutLine: false,
        printText: false,
        statement: null,
        ...overrides,
    }
}

/**
 * How a text field's text is shown over the page: in the size it prints at, scaled with the page.
 */
describe('fieldTextLook', () => {
    it('scales the size in points to the page as drawn', () => {
        expect(fieldTextLook(field({fontSize: 12}), 1.5, () => undefined)?.fontSizePx).toBe(18)
    })

    it('carries the alignment, the style, the wrapping and the font', () => {
        const look = fieldTextLook(
            field({align: TextAlign.CENTER, fontStyle: FontStyle.BOLD_ITALIC, wrap: true, fontFamily: 'Hausschrift'}),
            1,
            family => family === 'Hausschrift' ? '"Hausschrift", sans-serif' : undefined,
        )

        expect(look).toEqual({
            fontSizePx: 12,
            align: 'center',
            bold: true,
            italic: true,
            wrap: true,
            fontFamily: '"Hausschrift", sans-serif',
        })
    })

    it('sets no text for a check mark or a signature', () => {
        expect(fieldTextLook(field({kind: PdfFieldKind.CHECK}), 1, () => undefined)).toBeNull()
        expect(fieldTextLook(field({kind: PdfFieldKind.SIGNATURE}), 1, () => undefined)).toBeNull()
    })
})
