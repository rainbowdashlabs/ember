/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, type InjectionKey, type Ref} from 'vue'
import {
    DateKind,
    DocumentLanguage,
    type DateFormatCheck,
    type DateFormatOption,
    type DateTokenOption,
    type Placeholder,
} from '@/api/generated/schema'
import type {PlaceholderLabels} from '@/components/input/markdowneditor/placeholderChip'
import {parsePlaceholderKey} from '@/util/placeholders'

/**
 * The date formats of the catalogue, the language a template prints its dates in, and the server's check
 * of an own format.
 *
 * @property formats the ready-made formats
 * @property tokens  the letters an own format is written with, each with how it prints the example day
 * @property check   asks the server for the example day in an own format, or why it cannot be printed
 */
export interface PlaceholderDates {
    formats: readonly DateFormatOption[]
    tokens: readonly DateTokenOption[]
    language: DocumentLanguage
    check: (pattern: string, clock: boolean) => Promise<DateFormatCheck>
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

function inLanguage(option: {german: string, english: string}, language: DocumentLanguage): string {
    return language === DocumentLanguage.EN ? option.english : option.german
}

/**
 * @param option   a ready-made format
 * @param language the language of the template
 * @returns the format in the tokens of an own format
 */
export function presetPattern(option: DateFormatOption, language: DocumentLanguage): string {
    return inLanguage(option, language)
}

/**
 * The example day in a format the server takes, each token as the server prints it and everything
 * between them as written. It only shows a date: whether an own format can be printed is the server's
 * to say.
 *
 * @param pattern the format in tokens
 * @param dates   the tokens and the template's language
 * @returns the example, or null where the format holds no token
 */
export function exampleIn(pattern: string, dates: PlaceholderDates): string | null {
    let printed = false
    const example = pattern.replace(/(.)\1*/gu, run => {
        const token = dates.tokens.find(candidate => candidate.written === run)
        if (!token) return run
        printed = true
        return inLanguage(token, dates.language)
    })
    return printed ? example : null
}

/**
 * A format as a key names it after the bar, on the example day: a ready-made one by its name, anything
 * else as an own one.
 *
 * @param format what the key names after the bar
 * @param dates  the ready-made formats, the tokens and the template's language
 */
function exampleOf(format: string, dates: PlaceholderDates): string | null {
    const preset = dates.formats.find(option => option.written === format)
    return exampleIn(preset ? presetPattern(preset, dates.language) : format, dates)
}

/**
 * What every key of a template is called in the editor: the label of its value, and for a date in a
 * format the example day in that format after it, such as "Geburtsdatum (3. Oktober 2026)".
 *
 * @param placeholders the catalogue
 * @param dates        the ready-made formats, the tokens and the template's language
 */
export function placeholderLabels(placeholders: readonly Placeholder[], dates: PlaceholderDates): PlaceholderLabels {
    const labels: ReadonlyMap<string, string> = new Map(placeholders.map(placeholder => [placeholder.key, placeholder.label]))
    return {
        get(key: string) {
            const {base, format} = parsePlaceholderKey(key)
            const label = labels.get(base)
            if (label === undefined || format === null) return label
            const example = exampleOf(format, dates)
            return example === null ? label : `${label} (${example})`
        },
    }
}
