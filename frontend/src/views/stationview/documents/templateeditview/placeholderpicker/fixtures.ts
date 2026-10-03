/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {PlaceholderCategory, PlaceholderGroup, type Placeholder} from '@/api/generated/schema'

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
        group: PlaceholderGroup.MEMBER,
        category,
        path,
        informal: false,
        eventOnly: false,
        ...extra,
    }
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
        {label: 'Erziehungsberechtigte 1: Allergien', group: PlaceholderGroup.GUARDIAN}),
    placeholderOf('event.name', PlaceholderCategory.APPOINTMENT, ['Termin', 'Termin'], {eventOnly: true}),
]
