/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {DocumentLanguage, PlaceholderCategory} from '@/api/generated/schema'
import {APPOINTMENT_START, BIRTH_DATE, CATALOGUE, DATE_FORMATS, placeholderOf} from './fixtures'
import {formatsOf, placeholderLabels} from './placeholderDates'

const TODAY = placeholderOf('today', PlaceholderCategory.DOCUMENT, ['Dokument', 'Heutiges Datum'], {dateKind: BIRTH_DATE.dateKind})
const JOIN_DATE = placeholderOf('member.joinDate', PlaceholderCategory.MEMBER, ['Mitglied', 'Stammdaten', 'Mitglied seit'],
    {dateKind: BIRTH_DATE.dateKind})

describe('the labels of keys', () => {
    const labels = placeholderLabels([...CATALOGUE, BIRTH_DATE, TODAY, JOIN_DATE], {formats: DATE_FORMATS, language: DocumentLanguage.DE})

    it('carry the example day of a date in its format', () => {
        expect(labels.get('member.birthDate')).toBe('Geburtsdatum')
        expect(labels.get('member.birthDate|weekday')).toBe('Geburtsdatum (Samstag, 3. Oktober 2026)')
        expect(labels.get('member.birthDate|T.M.')).toBe('Geburtsdatum (3.10.)')
        expect(labels.get('member.birthDate|QQ')).toBe('Geburtsdatum')
        expect(labels.get('member.firstName')).toBe('Vorname')
        expect(labels.get('member.shoeSize')).toBeUndefined()
    })

    it('name the earlier keys of one format like the date in that format', () => {
        expect(labels.get('today.long')).toBe('Heutiges Datum (3. Oktober 2026)')
        expect(labels.get('member.joinDate.monthYear')).toBe('Mitglied seit (Oktober 2026)')
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
