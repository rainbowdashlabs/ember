/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {DocumentLanguage} from '@/api/generated/schema'

/**
 * A letter run of an own date format and what it prints.
 *
 * @property written how it is written, such as `TT`
 * @property name    the word its meaning is listed under in the help centre
 * @property clock   whether it prints a time of day, which a plain day does not have
 * @property print   what it prints for a date in a locale
 */
export interface DateToken {
    written: string
    name: string
    clock: boolean
    print: (date: Date, locale: string) => string
}

/** A piece of an own date format: a token, or a separator printed as it is. */
export type DatePart = {token: DateToken} | {text: string}

/** Why an own date format cannot be printed. */
export type DateFormatProblem = 'empty' | 'tooLong' | 'unknown' | 'noToken'

/** An own date format read into its parts, or the reason it cannot be. */
export type CompiledDateFormat =
    | {ok: true, parts: DatePart[], clock: boolean}
    | {ok: false, problem: DateFormatProblem, detail?: string}

/** The most characters an own date format may have, as the server takes it. */
export const DATE_FORMAT_MAX_LENGTH = 40

/** The signs an own date format may hold between its tokens. */
export const DATE_FORMAT_SEPARATORS = ' .,:/-'

/** @returns the day the editor shows every date format for: a Saturday in October, at half past six in the evening */
export function exampleDate(): Date {
    return new Date(2026, 9, 3, 18, 30)
}

function twoDigits(value: number): string {
    return String(value).padStart(2, '0')
}

/**
 * A word of a date as it stands within a whole date, which is how the server prints it: `Okt.` within a
 * date, not `Okt` on its own.
 */
function wordOf(date: Date, locale: string, options: Intl.DateTimeFormatOptions, type: Intl.DateTimeFormatPartTypes): string {
    return new Intl.DateTimeFormat(locale, {day: 'numeric', year: 'numeric', ...options})
        .formatToParts(date)
        .find(part => part.type === type)?.value ?? ''
}

/**
 * The letters an own date format is written with, German as the people writing templates read them: T for
 * Tag, M for Monat, J for Jahr, h for Stunde and m for Minute. The server reads the same set (`DateToken`),
 * and the help centre lists this table.
 */
export const DATE_TOKENS: readonly DateToken[] = [
    {written: 'T', name: 'day', clock: false, print: date => String(date.getDate())},
    {written: 'TT', name: 'dayTwoDigits', clock: false, print: date => twoDigits(date.getDate())},
    {written: 'TTT', name: 'weekdayShort', clock: false,
        print: (date, locale) => wordOf(date, locale, {weekday: 'short', month: 'long'}, 'weekday')},
    {written: 'TTTT', name: 'weekday', clock: false,
        print: (date, locale) => wordOf(date, locale, {weekday: 'long', month: 'long'}, 'weekday')},
    {written: 'M', name: 'month', clock: false, print: date => String(date.getMonth() + 1)},
    {written: 'MM', name: 'monthTwoDigits', clock: false, print: date => twoDigits(date.getMonth() + 1)},
    {written: 'MMM', name: 'monthShort', clock: false, print: (date, locale) => wordOf(date, locale, {month: 'short'}, 'month')},
    {written: 'MMMM', name: 'monthName', clock: false, print: (date, locale) => wordOf(date, locale, {month: 'long'}, 'month')},
    {written: 'JJ', name: 'yearTwoDigits', clock: false, print: date => twoDigits(date.getFullYear() % 100)},
    {written: 'JJJJ', name: 'year', clock: false, print: date => String(date.getFullYear()).padStart(4, '0')},
    {written: 'h', name: 'hour', clock: true, print: date => String(date.getHours())},
    {written: 'hh', name: 'hourTwoDigits', clock: true, print: date => twoDigits(date.getHours())},
    {written: 'mm', name: 'minute', clock: true, print: date => twoDigits(date.getMinutes())},
]

/**
 * Reads an own date format the way the server does: runs of one letter are tokens, the separators stand
 * as they are, and anything else is refused, as is a format without a token or longer than the server
 * takes.
 *
 * @param pattern the format as written
 * @returns its parts and whether it prints a time of day, or why it cannot be printed
 */
export function compileDateFormat(pattern: string): CompiledDateFormat {
    if (pattern.trim().length === 0) return {ok: false, problem: 'empty'}
    if (pattern.length > DATE_FORMAT_MAX_LENGTH) return {ok: false, problem: 'tooLong'}
    const parts: DatePart[] = []
    for (const [run] of pattern.matchAll(/(.)\1*/gu)) {
        if (DATE_FORMAT_SEPARATORS.includes(run[0] ?? '')) {
            parts.push({text: run})
            continue
        }
        const token = DATE_TOKENS.find(candidate => candidate.written === run)
        if (!token) return {ok: false, problem: 'unknown', detail: run}
        parts.push({token})
    }
    const tokens = parts.filter(part => 'token' in part)
    if (tokens.length === 0) return {ok: false, problem: 'noToken'}
    return {ok: true, parts, clock: tokens.some(part => part.token.clock)}
}

/**
 * @param language the language of a template
 * @returns the locale its month and weekday names are written in
 */
export function dateLocale(language: DocumentLanguage): string {
    return language === DocumentLanguage.EN ? 'en' : 'de'
}

/**
 * A date as an own format prints it.
 *
 * @param parts    the format's parts
 * @param date     the date
 * @param language the language of the template
 */
export function printDate(parts: readonly DatePart[], date: Date, language: DocumentLanguage): string {
    const locale = dateLocale(language)
    return parts.map(part => 'token' in part ? part.token.print(date, locale) : part.text).join('')
}
