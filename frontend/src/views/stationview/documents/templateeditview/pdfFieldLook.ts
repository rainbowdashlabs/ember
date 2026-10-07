/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FontStyle, PdfFieldKind, TextAlign, type PdfField} from '@/api/generated/schema'

/** How the text of a field looks over the page as it is drawn on screen. */
export interface FieldTextLook {
    fontSizePx: number
    align: 'left' | 'center' | 'right'
    bold: boolean
    italic: boolean
    wrap: boolean
    /** The CSS `font-family` the field's font shows in, or undefined for the page's default look. */
    fontFamily: string | undefined
}

const ALIGN: Readonly<Record<TextAlign, FieldTextLook['align']>> = {
    [TextAlign.LEFT]: 'left',
    [TextAlign.CENTER]: 'center',
    [TextAlign.RIGHT]: 'right',
}

/**
 * The look of a text field's text on screen: its size in points times the scale the page is drawn at,
 * and its alignment, style, wrapping and font. A check mark and a signature field have no text to set.
 *
 * @param field      the field
 * @param scale      CSS pixels per point of the page as drawn
 * @param fontFamily the CSS `font-family` for a family the template names, undefined where none is loaded
 * @return the look, or null for a field without text
 */
export function fieldTextLook(
    field: PdfField,
    scale: number,
    fontFamily: (family: string | null) => string | undefined,
): FieldTextLook | null {
    if (field.kind !== PdfFieldKind.TEXT) return null
    const style = field.fontStyle ?? FontStyle.REGULAR
    return {
        fontSizePx: field.fontSize * scale,
        align: ALIGN[field.align] ?? 'left',
        bold: style === FontStyle.BOLD || style === FontStyle.BOLD_ITALIC,
        italic: style === FontStyle.ITALIC || style === FontStyle.BOLD_ITALIC,
        wrap: field.wrap,
        fontFamily: fontFamily(field.fontFamily ?? null),
    }
}
