/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberGroupSet, MemberWithName, MergedField, MyInventoryItem} from '@/api/generated/schema'
import type {MemberGroup, PermissionGrant, UserTag} from '@/api/types'

/**
 * Everything the member edit tabs render, loaded once by the view and handed
 * down as a single bundle so the tab dispatcher stays free of pass-through props.
 */
export interface MemberEditData {
    fields: MergedField[]
    values: Map<string, string>
    allRoles: PermissionGrant[]
    allGroups: MemberGroup[]
    allSets: MemberGroupSet[]
    allTags: UserTag[]
    allMembers: MemberWithName[]
    userType: string
    roleIds: Set<number>
    groupIds: Set<number>
    tagIds: Set<number>
    lockedPermissions: Map<string, string>
    memberInventory: MyInventoryItem[]
}
