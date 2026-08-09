/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ProfileField} from '@/api/profileFields'
import type {MyInventoryItem} from '@/api/inventory'
import type {MemberGroup, PermissionGrant, StationMember, UserTag} from '@/api/types'

/**
 * Everything the member edit tabs render, loaded once by the view and handed
 * down as a single bundle so the tab dispatcher stays free of pass-through props.
 */
export interface MemberEditData {
    fields: ProfileField[]
    values: Map<number, string>
    allRoles: PermissionGrant[]
    allGroups: MemberGroup[]
    allTags: UserTag[]
    allMembers: StationMember[]
    userType: string
    roleIds: Set<number>
    groupIds: Set<number>
    tagIds: Set<number>
    lockedPermissions: Map<string, string>
    memberInventory: MyInventoryItem[]
}
