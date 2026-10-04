/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FontStyle, type DocumentFontView, type FontFamilyOption, type FontOrigin} from '@/api/generated/schema'

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

/**
 * A family name as the server compares names: trimmed and without case. The default font, named by
 * null, is the empty key.
 */
export function familyKey(family: string | null): string {
    return family ? family.trim().toLocaleLowerCase('de') : ''
}

/** Whether two family names name one family, compared as the server compares them. */
export function sameFamily(a: string, b: string): boolean {
    return familyKey(a) === familyKey(b)
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

/** Where a font of a list comes from: a family's origin, or the default font. */
export type EntryOrigin = FontOrigin | 'DEFAULT'

/**
 * What a font list shows for one choice.
 *
 * @property value   the choice it stands for
 * @property name    what it is called
 * @property origin  where it comes from, or null where the list says nothing about it
 * @property sample  the family a picture of sample text shows, null for the default font, and the
 *                   version of that picture; null where the entry shows none
 * @property missing whether the template names the family but no longer reaches it
 */
export interface FontEntry {
    value: string
    name: string
    origin: EntryOrigin | null
    sample: {family: string | null, version: string} | null
    missing: boolean
}

/**
 * How a list shows its default entry: by the family the default font is, with its origin and a sample,
 * or, where the default is something the list cannot draw (the font around the words), by a name alone.
 */
export type DefaultEntry = {family: string} | {name: string}

/**
 * The entries of a font list, one per choice and in the same order.
 *
 * @param choices      the choices, as {@link familyChoices} makes them
 * @param defaultEntry how the default entry reads
 */
export function fontEntries(choices: readonly FamilyChoice[], defaultEntry: DefaultEntry): FontEntry[] {
    return choices.map(choice => {
        if (choice.missing) return {value: choice.value, name: choice.value, origin: null, sample: null, missing: true}
        const family = choice.family
        if (family) {
            return {
                value: choice.value,
                name: family.family,
                origin: family.origin,
                sample: {family: family.family, version: family.sample},
                missing: false,
            }
        }
        if ('family' in defaultEntry) {
            const sample = {family: null, version: defaultEntry.family}
            return {value: '', name: defaultEntry.family, origin: 'DEFAULT', sample, missing: false}
        }
        return {value: '', name: defaultEntry.name, origin: null, sample: null, missing: false}
    })
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
 * The family a template names, as it reaches it.
 *
 * @param reachable the families the template reaches
 * @param family    the family it names, or null for the default font
 * @returns the family, or null for the default font and a family no longer reached, which both print in it
 */
export function reachedFamily(reachable: readonly FontFamilyOption[], family: string | null): FontFamilyOption | null {
    if (!family) return null
    return reachable.find(option => sameFamily(option.family, family)) ?? null
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
