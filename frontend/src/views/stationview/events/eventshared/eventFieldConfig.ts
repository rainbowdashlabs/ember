/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {EventFieldTypes} from '@/api/events'

export const MEMBER_FIELD_TYPES: string[] = [
    EventFieldTypes.MEMBER,
    EventFieldTypes.MEMBER_LIST,
    EventFieldTypes.MEMBER_OF_GROUP,
    EventFieldTypes.MEMBER_LIST_OF_GROUP,
    EventFieldTypes.MEMBER_OF_TYPE,
    EventFieldTypes.MEMBER_LIST_OF_TYPE,
    EventFieldTypes.MEMBER_OF_TAG,
    EventFieldTypes.MEMBER_LIST_OF_TAG,
]

export type FieldConstraint = 'group' | 'userType' | 'tag' | null

/**
 * Which extra selection a member field narrows its candidates by, or null when
 * the field type carries no such restriction.
 */
export function fieldConstraint(fieldType: string): FieldConstraint {
    switch (fieldType) {
        case EventFieldTypes.MEMBER_OF_GROUP:
        case EventFieldTypes.MEMBER_LIST_OF_GROUP:
            return 'group'
        case EventFieldTypes.MEMBER_OF_TYPE:
        case EventFieldTypes.MEMBER_LIST_OF_TYPE:
            return 'userType'
        case EventFieldTypes.MEMBER_OF_TAG:
        case EventFieldTypes.MEMBER_LIST_OF_TAG:
            return 'tag'
        default:
            return null
    }
}

export function isMemberFieldType(fieldType: string): boolean {
    return MEMBER_FIELD_TYPES.includes(fieldType)
}
