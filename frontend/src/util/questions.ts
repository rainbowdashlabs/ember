/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The kinds of question anything in Ember asks, as the browser knows them.
 *
 * <p>The twin of the kinds the server measures answers against. Six features each named their own
 * types, and the names overlap almost entirely: what differs is which of them a feature offers and
 * what it calls the same thing.
 */
export const QuestionKinds = {
    TEXT: 'TEXT',
    LONG_TEXT: 'LONG_TEXT',
    NUMBER: 'NUMBER',
    DECIMAL: 'DECIMAL',
    DATE: 'DATE',
    TIME: 'TIME',
    BOOLEAN: 'BOOLEAN',
    CHOICE: 'CHOICE',
    URL: 'URL',
    MEMBER: 'MEMBER',
    MEMBER_LIST: 'MEMBER_LIST',
} as const

export type QuestionKindName = (typeof QuestionKinds)[keyof typeof QuestionKinds]

/**
 * The kind a feature's own field type is, or nothing where it asks nobody anything.
 *
 * <p>Read off the name, because the six features spell the same kinds the same way: a heading asks
 * nothing, a birth date is a date, an age is a number, and the nine ways of naming members are two
 * kinds, one member or several.
 *
 * @param fieldType the type as the feature stores it
 * @param whole     whether a number of this feature refuses a fraction, which only the appointment
 *                  questions do: a count of guests is whole, a length in metres is not
 */
export function questionKindOf(fieldType: string | undefined | null, whole = false): QuestionKindName | null {
    if (!fieldType) return null
    const type = fieldType.toUpperCase()
    if (type === 'SECTION') return null
    if (type.startsWith('MEMBER')) {
        return type.includes('_LIST') ? QuestionKinds.MEMBER_LIST : QuestionKinds.MEMBER
    }
    switch (type) {
        case 'NUMBER':
        case 'AGE':
            return whole ? QuestionKinds.NUMBER : QuestionKinds.DECIMAL
        case 'DATE':
        case 'BIRTH_DATE':
            return QuestionKinds.DATE
        case 'TIME':
            return QuestionKinds.TIME
        case 'BOOLEAN':
            return QuestionKinds.BOOLEAN
        case 'ENUM':
            return QuestionKinds.CHOICE
        case 'URL':
            return QuestionKinds.URL
        case 'TEXTAREA':
            return QuestionKinds.LONG_TEXT
        default:
            return QuestionKinds.TEXT
    }
}

/**
 * The members an answer names, in the order it names them.
 *
 * <p>One member is written as a bare number and several as a JSON array, and both shapes are read,
 * exactly as the server reads them. Three screens had their own copy of this.
 */
export function memberIdsOf(value: string | null | undefined): string[] {
    if (!value) return []
    const trimmed = value.trim()
    if (!trimmed) return []
    if (trimmed.startsWith('[')) {
        try {
            const parsed: unknown = JSON.parse(trimmed)
            if (Array.isArray(parsed)) return parsed.map(entry => String(entry)).filter(entry => entry !== '')
        } catch {
            return []
        }
        return []
    }
    return [trimmed.replace(/"/g, '')]
}

/** Members as an answer stores them: one on its own, several as a list. */
export function formatMemberIds(ids: string[]): string {
    if (ids.length === 0) return ''
    if (ids.length === 1) return ids[0] ?? ''
    return JSON.stringify(ids.map(Number))
}
