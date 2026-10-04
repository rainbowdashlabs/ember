/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, type InjectionKey, type Ref} from 'vue'
import {DateKind, DocumentLanguage, type DateFormatOption, type Placeholder} from '@/api/generated/schema'
import {compileDateFormat, exampleDate, printDate, type CompiledDateFormat} from '@/util/dateFormatPattern'
import type {PlaceholderLabels} from '@/components/input/markdowneditor/placeholderChip'
import {parsePlaceholderKey} from '@/util/placeholders'

/** The ready-made date formats of the catalogue, and the language a template prints its dates in. */
export interface PlaceholderDates {
    formats: readonly DateFormatOption[]
    language: DocumentLanguage
}

const PLACEHOLDER_DATES: InjectionKey<Readonly<Ref<PlaceholderDates>>> = Symbol('placeholderDates')

/**
 * Hands the placeholder pickers below the date formats to offer and the language their examples are
 * written in, which is the template's.
 */
export function providePlaceholderDates(dates: Readonly<Ref<PlaceholderDates>>): void {
    provide(PLACEHOLDER_DATES, dates)
}

/** The date formats to offer, or null where no editor above names them and dates are inserted as they are. */
export function usePlaceholderDates(): Readonly<Ref<PlaceholderDates>> | null {
    return inject(PLACEHOLDER_DATES, null)
}

/**
 * The ready-made formats a placeholder offers: none for a value that holds no date, those without a time
 * of day for a day, and all of them for a day with a time.
 *
 * @param placeholder a placeholder of the catalogue
 * @param formats     every ready-made format
 */
export function formatsOf(placeholder: Placeholder, formats: readonly DateFormatOption[]): DateFormatOption[] {
    if (placeholder.dateKind === null) return []
    return formats.filter(format => format.kind === DateKind.DATE || placeholder.dateKind === DateKind.DATE_TIME)
}

/**
 * @param option   a ready-made format
 * @param language the language of the template
 * @returns the format in the tokens of an own format
 */
export function presetPattern(option: DateFormatOption, language: DocumentLanguage): string {
    return language === DocumentLanguage.EN ? option.english : option.german
}

/**
 * A format as a key names it after the bar, read: a ready-made one by its name, anything else as an own
 * one.
 *
 * @param format what the key names after the bar
 * @param dates  the ready-made formats and the template's language
 */
export function compileWritten(format: string, dates: PlaceholderDates): CompiledDateFormat {
    const preset = dates.formats.find(option => option.written === format)
    return compileDateFormat(preset ? presetPattern(preset, dates.language) : format)
}

/**
 * @param compiled a format read into its parts
 * @param language the language of the template
 * @returns the example day in that format, or null where the format cannot be printed
 */
export function exampleOf(compiled: CompiledDateFormat, language: DocumentLanguage): string | null {
    return compiled.ok ? printDate(compiled.parts, exampleDate(), language) : null
}

/**
 * What every key of a template is called in the editor: the label of its value, and for a date in a
 * format the example day in that format after it, such as "Geburtsdatum (3. Oktober 2026)". The two
 * keys that named a date in one format before are called the same way.
 *
 * @param placeholders the catalogue
 * @param dates        the ready-made formats and the template's language
 */
export function placeholderLabels(placeholders: readonly Placeholder[], dates: PlaceholderDates): PlaceholderLabels {
    const labels: ReadonlyMap<string, string> = new Map(placeholders.map(placeholder => [placeholder.key, placeholder.label]))
    return {
        get(key: string) {
            const {base, format} = parsePlaceholderKey(key)
            const label = labels.get(base)
            if (label === undefined || format === null) return label
            const example = exampleOf(compileWritten(format, dates), dates.language)
            return example === null ? label : `${label} (${example})`
        },
    }
}
