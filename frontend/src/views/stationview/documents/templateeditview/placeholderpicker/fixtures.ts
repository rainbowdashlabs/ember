/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    DateKind,
    PlaceholderCategory,
    type DateFormatCheck,
    type DateFormatOption,
    type DateTokenOption,
    type DocumentLanguage,
    type Placeholder,
} from '@/api/generated/schema'
import {exampleIn, type PlaceholderDates} from './placeholderDates'

/**
 * A placeholder of a station's catalogue for the picker's tests.
 *
 * @param key      its key
 * @param category its category
 * @param path     its path, the category's word first and its own name last
 * @param extra    anything that differs from an ordinary value
 */
export function placeholderOf(key: string, category: PlaceholderCategory, path: string[], extra: Partial<Placeholder> = {}): Placeholder {
    return {
        key,
        label: path.at(-1) ?? key,
        category,
        path,
        informal: false,
        eventOnly: false,
        dateKind: null,
        ...extra,
    }
}

/** A day of the member, which takes the date formats. */
export const BIRTH_DATE = placeholderOf('member.birthDate', PlaceholderCategory.MEMBER, ['Mitglied', 'Stammdaten', 'Geburtsdatum'],
    {dateKind: DateKind.DATE})

/** A day with a time of day, which also takes the formats that print hours and minutes. */
export const APPOINTMENT_START = placeholderOf('event.start', PlaceholderCategory.APPOINTMENT, ['Termin', 'Beginn des Termins'],
    {eventOnly: true, dateKind: DateKind.DATE_TIME})

/** The ready-made formats as the server sends them, in its order. */
export const DATE_FORMATS: DateFormatOption[] = [
    {written: 'short', kind: DateKind.DATE, german: 'TT.MM.JJJJ', english: 'TT.MM.JJJJ'},
    {written: 'long', kind: DateKind.DATE, german: 'T. MMMM JJJJ', english: 'MMMM T, JJJJ'},
    {written: 'medium', kind: DateKind.DATE, german: 'T. MMM JJJJ', english: 'MMM T, JJJJ'},
    {written: 'monthYear', kind: DateKind.DATE, german: 'MMMM JJJJ', english: 'MMMM JJJJ'},
    {written: 'year', kind: DateKind.DATE, german: 'JJJJ', english: 'JJJJ'},
    {written: 'weekday', kind: DateKind.DATE, german: 'TTTT, T. MMMM JJJJ', english: 'TTTT, MMMM T, JJJJ'},
    {written: 'dateTime', kind: DateKind.DATE_TIME, german: 'TT.MM.JJJJ hh:mm', english: 'TT.MM.JJJJ hh:mm'},
    {written: 'time', kind: DateKind.DATE_TIME, german: 'hh:mm', english: 'hh:mm'},
]

/** The tokens of an own format as the server sends them, each with the example day in both languages. */
export const DATE_TOKENS: DateTokenOption[] = [
    {written: 'T', clock: false, german: '3', english: '3'},
    {written: 'TT', clock: false, german: '03', english: '03'},
    {written: 'TTT', clock: false, german: 'Sa.', english: 'Sat'},
    {written: 'TTTT', clock: false, german: 'Samstag', english: 'Saturday'},
    {written: 'M', clock: false, german: '10', english: '10'},
    {written: 'MM', clock: false, german: '10', english: '10'},
    {written: 'MMM', clock: false, german: 'Okt.', english: 'Oct'},
    {written: 'MMMM', clock: false, german: 'Oktober', english: 'October'},
    {written: 'JJ', clock: false, german: '26', english: '26'},
    {written: 'JJJJ', clock: false, german: '2026', english: '2026'},
    {written: 'h', clock: true, german: '18', english: '18'},
    {written: 'hh', clock: true, german: '18', english: '18'},
    {written: 'mm', clock: true, german: '30', english: '30'},
]

/**
 * The date formats of a template for the picker's tests, with a check of own formats that answers the
 * way the server does for the formats the tests type.
 *
 * @param language the template's language
 * @param answers  what the check answers per format; anything else is printed on the example day
 */
export function datesOf(language: DocumentLanguage, answers: Record<string, DateFormatCheck> = {}): PlaceholderDates {
    const dates: PlaceholderDates = {
        formats: DATE_FORMATS,
        tokens: DATE_TOKENS,
        language,
        check: async pattern => answers[pattern] ?? {example: exampleIn(pattern, dates), problem: null, detail: null},
    }
    return dates
}

/** A catalogue as a station sends it, in its order: the member, pronouns, a guardian, appointment, signature. */
export const CATALOGUE: Placeholder[] = [
    placeholderOf('member.firstName', PlaceholderCategory.MEMBER, ['Mitglied', 'Stammdaten', 'Vorname']),
    placeholderOf('member.calledName', PlaceholderCategory.MEMBER, ['Mitglied', 'Stammdaten', 'Rufname'], {informal: true}),
    placeholderOf('profile.1', PlaceholderCategory.MEMBER, ['Mitglied', 'Profil', 'Schule']),
    placeholderOf('profile.2', PlaceholderCategory.MEMBER, ['Mitglied', 'Profil', 'Medizinisches', 'Allergien']),
    placeholderOf('pronoun.subject', PlaceholderCategory.PRONOUNS, ['Pronomen', 'Wer (er / sie)', 'er / sie / Vorname']),
    placeholderOf('pronoun.possessive.start.e', PlaceholderCategory.PRONOUNS,
        ['Pronomen', 'Wessen (sein / ihr)', 'Am Satzanfang', 'Seine / Ihre / Vornamens'],
        {label: 'Seine / Ihre / Vornamens (Satzanfang)'}),
    placeholderOf('guardian1.profile.2', PlaceholderCategory.GUARDIAN1,
        ['Erziehungsberechtigte 1', 'Profil', 'Medizinisches', 'Allergien'],
        {label: 'Erziehungsberechtigte 1: Allergien'}),
    placeholderOf('event.name', PlaceholderCategory.APPOINTMENT, ['Termin', 'Termin'], {eventOnly: true}),
]
