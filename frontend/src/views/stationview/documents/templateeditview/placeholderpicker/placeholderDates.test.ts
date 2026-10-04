/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {DocumentLanguage, PlaceholderCategory} from '@/api/generated/schema'
import {APPOINTMENT_START, BIRTH_DATE, CATALOGUE, DATE_FORMATS, datesOf, placeholderOf} from './fixtures'
import {exampleIn, formatsOf, placeholderLabels} from './placeholderDates'

const TODAY = placeholderOf('today', PlaceholderCategory.DOCUMENT, ['Dokument', 'Heutiges Datum'], {dateKind: BIRTH_DATE.dateKind})
const JOIN_DATE = placeholderOf('member.joinDate', PlaceholderCategory.MEMBER, ['Mitglied', 'Stammdaten', 'Mitglied seit'],
    {dateKind: BIRTH_DATE.dateKind})

describe('the labels of keys', () => {
    const labels = placeholderLabels([...CATALOGUE, BIRTH_DATE, TODAY, JOIN_DATE], datesOf(DocumentLanguage.DE))

    it('carry the example day of a date in its format', () => {
        expect(labels.get('member.birthDate')).toBe('Geburtsdatum')
        expect(labels.get('member.birthDate|weekday')).toBe('Geburtsdatum (Samstag, 3. Oktober 2026)')
        expect(labels.get('member.birthDate|T.M.')).toBe('Geburtsdatum (3.10.)')
        expect(labels.get('member.birthDate|QQ')).toBe('Geburtsdatum')
        expect(labels.get('member.firstName')).toBe('Vorname')
        expect(labels.get('member.shoeSize')).toBeUndefined()
    })
})

describe('the example day in a format', () => {
    it('prints each token as the server does and keeps what stands between them', () => {
        expect(exampleIn('TT.MM.JJ hh:mm / T-M', datesOf(DocumentLanguage.DE))).toBe('03.10.26 18:30 / 3-10')
        expect(exampleIn('TTT, T. MMM JJJJ', datesOf(DocumentLanguage.EN))).toBe('Sat, 3. Oct 2026')
        expect(exampleIn(' .-', datesOf(DocumentLanguage.DE))).toBeNull()
    })
})

describe('the formats a placeholder offers', () => {
    it('are none for a value without a date, those without a time for a day and all for a day with a time', () => {
        expect(formatsOf(CATALOGUE[0]!, DATE_FORMATS)).toEqual([])
        expect(formatsOf(BIRTH_DATE, DATE_FORMATS).map(format => format.written))
            .toEqual(['short', 'long', 'medium', 'monthYear', 'year', 'weekday'])
        expect(formatsOf(APPOINTMENT_START, DATE_FORMATS)).toEqual(DATE_FORMATS)
    })
})
