/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldType} from './generated/schema'

const T = FieldType

/**
 * The types each feature's type picker offers, in the order it offers them.
 *
 * <p>Profile, association, inventory, board, appointment, registration, attendance and waiting list
 * fields all pick from the one list of field types. Each list is part of what the server accepts for
 * that feature, and is exactly what the feature's screen offered before the names were shared. Where a
 * screen offers less than the server accepts, a field already saved under one of the others still
 * shows its own type in the picker.
 */
export const OfferedFieldTypes = {
    PROFILE: [
        T.TEXT, T.NUMBER, T.DATE, T.BIRTH_DATE, T.EXPIRY_DATE, T.BOOLEAN, T.CHOICE, T.GENDER, T.AGE, T.SECTION,
        T.SPACER,
    ],
    ASSOCIATION: [T.TEXT, T.NUMBER, T.DATE, T.EXPIRY_DATE, T.BOOLEAN, T.CHOICE, T.GENDER, T.AGE, T.SECTION],
    INVENTORY: [T.DATE, T.CHOICE, T.TEXT, T.NUMBER, T.BOOLEAN],
    BOARD: [T.TEXT, T.NUMBER, T.BOOLEAN, T.CHOICE, T.DATE, T.LANE_ASSIGNEE],
    APPOINTMENT: [
        T.TEXT, T.NUMBER, T.TIME, T.DATE, T.BOOLEAN, T.CHOICE, T.URL, T.LONG_TEXT, T.LOCATION,
        T.MEMBER, T.MEMBER_LIST, T.MEMBER_OF_GROUP, T.MEMBER_LIST_OF_GROUP, T.MEMBER_OF_TYPE,
        T.MEMBER_LIST_OF_TYPE, T.MEMBER_OF_TAG, T.MEMBER_LIST_OF_TAG,
    ],
    REGISTRATION: [T.TEXT, T.LONG_TEXT, T.NUMBER, T.BOOLEAN, T.CHOICE, T.DATE, T.TIME, T.MEMBER],
    ATTENDANCE: [
        T.TEXT, T.TIME, T.DATE, T.BOOLEAN, T.CHOICE, T.MEMBER, T.MEMBER_LIST, T.MEMBER_OF_GROUP,
        T.MEMBER_LIST_OF_GROUP,
    ],
    WAITING_LIST: [T.TEXT, T.NUMBER, T.DATE, T.BIRTH_DATE, T.BOOLEAN, T.CHOICE],
} as const satisfies Record<string, readonly FieldType[]>

/** Which members a member field may name, or null where it names anybody or nobody. */
export type MemberConstraint = 'group' | 'userType' | 'tag' | null

const NAMES_MEMBERS: readonly FieldType[] = [
    T.MEMBER, T.MEMBER_LIST, T.MEMBER_OF_GROUP, T.MEMBER_LIST_OF_GROUP, T.MEMBER_OF_TYPE,
    T.MEMBER_LIST_OF_TYPE, T.MEMBER_OF_TAG, T.MEMBER_LIST_OF_TAG, T.LANE_ASSIGNEE,
]

const NAMES_SEVERAL: readonly FieldType[] = [
    T.MEMBER_LIST, T.MEMBER_LIST_OF_GROUP, T.MEMBER_LIST_OF_TYPE, T.MEMBER_LIST_OF_TAG,
]

const HOLDS_NOTHING: readonly FieldType[] = [T.AGE, T.SECTION, T.SPACER]

const DATES: readonly FieldType[] = [T.DATE, T.BIRTH_DATE, T.EXPIRY_DATE]

const CHOICES: readonly FieldType[] = [T.CHOICE, T.GENDER]

/**
 * Whether a field of this type is answered from its written-down options: a choice, and a gender, which
 * is a choice whose answers also carry pronouns.
 */
export function isChoiceType(type: string | null | undefined): boolean {
    return CHOICES.includes(type as FieldType)
}

/** Whether a field of this type names members, one or several. */
export function namesMembers(type: string | null | undefined): boolean {
    return NAMES_MEMBERS.includes(type as FieldType)
}

/** Whether a field of this type names several members rather than one. */
export function namesSeveralMembers(type: string | null | undefined): boolean {
    return NAMES_SEVERAL.includes(type as FieldType)
}

/** Whether a field of this type holds a value of its own; an age, a heading and a gap do not. */
export function holdsValue(type: string | null | undefined): boolean {
    return !!type && !HOLDS_NOTHING.includes(type as FieldType)
}

/** Whether a field of this type holds a calendar date, which is also what an age counts from. */
export function isDateType(type: string | null | undefined): boolean {
    return DATES.includes(type as FieldType)
}

/** Which members a member field of this type may name. */
export function memberConstraintOf(type: string | null | undefined): MemberConstraint {
    switch (type) {
        case T.MEMBER_OF_GROUP:
        case T.MEMBER_LIST_OF_GROUP:
            return 'group'
        case T.MEMBER_OF_TYPE:
        case T.MEMBER_LIST_OF_TYPE:
            return 'userType'
        case T.MEMBER_OF_TAG:
        case T.MEMBER_LIST_OF_TAG:
            return 'tag'
        default:
            return null
    }
}

/** Whether a name is one of the field types, which is what a value read from a select box is checked by. */
export function isFieldType(value: unknown): value is FieldType {
    return typeof value === 'string' && Object.hasOwn(FieldType, value)
}

type Translate = (key: string) => string

/** The name of a field type as every screen shows it, the bare value for one nobody translated. */
export function fieldTypeLabel(translate: Translate, type: string): string {
    const key = `fieldTypes.label.${type}`
    const label = translate(key)
    return label === key ? type : label
}
