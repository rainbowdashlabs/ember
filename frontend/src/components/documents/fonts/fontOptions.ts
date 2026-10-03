/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FontStyle, type DocumentFontView, type FontFamilyOption} from '@/api/generated/schema'

/** The font every text prints in that names no family, and the fallback for what a family lacks. */
export const DEFAULT_FONT = 'Liberation Sans'

/** The styles of a family in the order the screens list them. */
export const FONT_STYLES: readonly FontStyle[] = [FontStyle.REGULAR, FontStyle.BOLD, FontStyle.ITALIC, FontStyle.BOLD_ITALIC]

/**
 * One entry of a family picker.
 *
 * @property value   what the select holds: the family name, or the empty string for the default font
 * @property family  the family as the station reaches it, or null for the default font and a missing one
 * @property missing whether the template names a family the station no longer reaches
 */
export interface FamilyChoice {
    value: string
    family: FontFamilyOption | null
    missing: boolean
}

/** Whether two family names name one family, compared without case as the server compares them. */
export function sameFamily(a: string, b: string): boolean {
    return a.trim().toLocaleLowerCase('de') === b.trim().toLocaleLowerCase('de')
}

/**
 * What a family picker offers: the default font first, then every family the template can print in.
 * A family the template names but no longer reaches stays as a choice of its own, marked missing, so
 * opening the picker never quietly swaps it for something else; the document prints it in the default
 * font until it is reachable again or another one is chosen.
 *
 * @param reachable the families the owner's templates reach
 * @param current   the family the template names now, or null for the default font
 * @param pdfOnly   whether only families a field on an uploaded PDF can print in are offered
 */
export function familyChoices(
    reachable: readonly FontFamilyOption[],
    current: string | null,
    pdfOnly = false,
): FamilyChoice[] {
    const offered = reachable.filter(family => !pdfOnly || family.printsOnPdf)
    const choices: FamilyChoice[] = [
        {value: '', family: null, missing: false},
        ...offered.map(family => ({value: family.family, family, missing: false})),
    ]
    if (current && !offered.some(family => sameFamily(family.family, current))) {
        choices.push({value: current, family: null, missing: true})
    }
    return choices
}

/** The value a picker shows for a family a template names: the reached spelling, or the stored one. */
export function choiceValue(choices: readonly FamilyChoice[], current: string | null): string {
    if (!current) return ''
    return choices.find(choice => choice.value !== '' && sameFamily(choice.value, current))?.value ?? current
}

/** What a template stores for a picker's value: null for the default font. */
export function familyOf(value: string): string | null {
    const trimmed = value.trim()
    return trimmed.length > 0 ? trimmed : null
}

/**
 * The style a text is drawn in: the one asked for where the family has it, else its regular one, as the
 * server draws it. The default font is drawn in the style asked for.
 */
export function printedStyle(family: FontFamilyOption | null, style: FontStyle): FontStyle {
    if (!family || family.styles.includes(style)) return style
    return FontStyle.REGULAR
}

/**
 * How the preview draws a style. The browser never has the uploaded file, so the preview is set in
 * the browser's own sans serif font with the weight and slant of the style.
 */
export function previewStyle(style: FontStyle): {fontWeight: string; fontStyle: string} {
    const bold = style === FontStyle.BOLD || style === FontStyle.BOLD_ITALIC
    const italic = style === FontStyle.ITALIC || style === FontStyle.BOLD_ITALIC
    return {fontWeight: bold ? '700' : '400', fontStyle: italic ? 'italic' : 'normal'}
}

/** One family of an owner's own fonts, with a file per style it has. */
export interface OwnFamily {
    family: string
    fonts: DocumentFontView[]
}

/** An owner's fonts grouped by family, families by name and the files of one in style order. */
export function groupByFamily(fonts: readonly DocumentFontView[]): OwnFamily[] {
    const families: OwnFamily[] = []
    for (const font of fonts) {
        const group = families.find(family => sameFamily(family.family, font.family))
        if (group) group.fonts.push(font)
        else families.push({family: font.family, fonts: [font]})
    }
    for (const family of families) {
        family.fonts.sort((a, b) => FONT_STYLES.indexOf(a.style) - FONT_STYLES.indexOf(b.style))
    }
    return families.sort((a, b) => a.family.localeCompare(b.family, 'de'))
}
