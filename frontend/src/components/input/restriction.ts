/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type WritableComputedRef} from 'vue'
import type {RestrictionSelection} from '@/api/types'

export type {RestrictionSelection}

/**
 * A fresh, empty restriction selection. Use to initialise a `ref<RestrictionSelection>`
 * before loading persisted restrictions into it.
 */
export function emptyRestriction(): RestrictionSelection {
    return {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'}
}

/**
 * Presents three separate selections - user types, groups and tags - as the single
 * {@link RestrictionSelection} the shared restriction editor speaks, and writes edits back to
 * them.
 *
 * Several features store the three parts as separate fields rather than as one object, so this is
 * the adapter between that storage shape and the editor's. Individual members are not selectable
 * in these editors and the parts always combine with AND, so both are fixed here rather than
 * being carried by every caller.
 */
export function toRestrictionSelection(
    userTypes: {value: string[]},
    groupIds: {value: number[]},
    tagIds: {value: number[]},
): WritableComputedRef<RestrictionSelection> {
    return computed({
        get: (): RestrictionSelection => ({
            userTypes: userTypes.value,
            groupIds: groupIds.value,
            tagIds: tagIds.value,
            memberIds: [],
            mode: 'AND',
        }),
        set: (value: RestrictionSelection) => {
            userTypes.value = value.userTypes
            groupIds.value = value.groupIds
            tagIds.value = value.tagIds
        },
    })
}
