/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
export interface RestrictionSelection {
    userTypes: string[]
    groupIds: number[]
    tagIds: number[]
    memberIds: number[]
    mode: 'AND' | 'OR'
}

/**
 * A fresh, empty restriction selection. Use to initialise a `ref<RestrictionSelection>`
 * before loading persisted restrictions into it.
 */
export function emptyRestriction(): RestrictionSelection {
    return {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'}
}
