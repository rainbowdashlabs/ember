/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldTypes, isDateType, namesMembers, namesSeveralMembers} from '@/api/fieldTypes'
import {formatDate, formatTime} from '@/util/format'

/**
 * The kinds of question anything in Ember asks, as the browser knows them.
 *
 * <p>The twin of the kinds the server measures answers against. A field type is one of these, or
 * says something more on top of one: a birth date is a date, a place is a line of text, and the
 * nine ways of naming members are two kinds, one member or several.
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
 * The kind a field type is, or nothing where it asks nobody anything.
 *
 * <p>A heading and a gap ask nothing, and an age is counted from a date and asks nothing either. A
 * number is whole unless its step is below one, which is how a length in metres is told from a count
 * of people: the server reads it the same way.
 *
 * @param fieldType the field's type
 * @param step      what a number steps by, where the field says
 */
export function questionKindOf(
    fieldType: string | undefined | null,
    step?: number | null,
): QuestionKindName | null {
    if (!fieldType || fieldType === FieldTypes.SECTION || fieldType === FieldTypes.SPACER
        || fieldType === FieldTypes.AGE) {
        return null
    }
    if (namesMembers(fieldType)) {
        return namesSeveralMembers(fieldType) ? QuestionKinds.MEMBER_LIST : QuestionKinds.MEMBER
    }
    if (isDateType(fieldType)) return QuestionKinds.DATE
    switch (fieldType) {
        case FieldTypes.NUMBER:
            return step != null && step < 1 ? QuestionKinds.DECIMAL : QuestionKinds.NUMBER
        case FieldTypes.LONG_TEXT:
            return QuestionKinds.LONG_TEXT
        case FieldTypes.TIME:
            return QuestionKinds.TIME
        case FieldTypes.BOOLEAN:
            return QuestionKinds.BOOLEAN
        case FieldTypes.CHOICE:
            return QuestionKinds.CHOICE
        case FieldTypes.URL:
            return QuestionKinds.URL
        default:
            return QuestionKinds.TEXT
    }
}

/**
 * Whether an answer says yes.
 *
 * <p>A yes has been stored as a JSON {@code true}, as the text {@code "true"} and as {@code "1"}, and
 * screens that only knew one of those showed the others as no. Every one of them is read here.
 */
export function isYes(value: unknown): boolean {
    if (value === true || value === 1) return true
    if (typeof value !== 'string') return false
    const said = value.trim().replace(/"/g, '').toLowerCase()
    return said === 'true' || said === '1'
}

/**
 * The members an answer names, in the order it names them.
 *
 * <p>One member is written as a bare number and several as a JSON array, and both shapes are read,
 * exactly as the server reads them, whether the answer arrives as text or already parsed.
 */
export function memberIdsOf(value: unknown): string[] {
    if (value == null) return []
    if (Array.isArray(value)) return value.map(entry => String(entry)).filter(entry => entry !== '')
    if (typeof value === 'number') return Number.isFinite(value) ? [String(value)] : []
    if (typeof value !== 'string') return []
    const trimmed = value.trim()
    if (!trimmed) return []
    if (trimmed.startsWith('[')) {
        try {
            const parsed: unknown = JSON.parse(trimmed)
            return Array.isArray(parsed) ? memberIdsOf(parsed) : []
        } catch {
            return []
        }
    }
    return [trimmed.replace(/"/g, '')]
}

/** Members as an answer stores them: one on its own, several as a list. */
export function formatMemberIds(ids: string[]): string {
    if (ids.length === 0) return ''
    if (ids.length === 1) return ids[0] ?? ''
    return JSON.stringify(ids.map(Number))
}

/** What the reader's language calls yes and no, and whom the member ids name. */
export interface AnswerWords {
    yes: string
    no: string
    /** The station's members by id, for the types that name people; an unknown id reads as its number. */
    names?: Map<number, string>
}

/**
 * One answer as a reader would see it written out, for a line of text rather than a cell of its own.
 *
 * <p>An answer is stored the way a database wants it: a yes as {@code true}, a day as 2026-10-12, a
 * person as a number. Every screen that printed what was stored showed exactly that, so whatever
 * writes an answer into a sentence, a list or a table comes through here.
 *
 * @param fieldType the type of the field answered
 * @param value     the answer as stored, text or already parsed
 * @param words     the words for yes and no, and the names of the members
 * @return the answer as text, empty where there is none
 */
export function answerText(fieldType: string | null | undefined, value: unknown, words: AnswerWords): string {
    if (value == null || value === '') return ''
    if (fieldType === FieldTypes.BOOLEAN) return isYes(value) ? words.yes : words.no
    if (namesMembers(fieldType)) {
        return memberIdsOf(value)
            .map(id => words.names?.get(Number(id)) ?? `#${id}`)
            .join(', ')
    }
    const text = String(value)
    if (isDateType(fieldType)) return formatDate(text) || text
    if (fieldType === FieldTypes.TIME) return formatTime(text) || text
    return text
}
