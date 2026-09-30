/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {
    MemberGroup,
    PermissionGrant,
    StationMember,
    StationUserTypeName,
} from './types'

/** Which member types a group takes and which set it belongs to. */
export interface GroupRules {
    groupSetId: number | null
    userTypes: StationUserTypeName[]
}

export interface GroupRequest {
    name?: string
    color?: string | null
    position?: number
    /** The group's rules. Absent leaves them as they are. */
    rules?: GroupRules
    /** Whether members the new binding does not take are taken out of the group, rather than refused. */
    removeNonMatching?: boolean
}

export interface GroupDetail {
    id: number
    stationId: string
    name?: string
    members?: StationMember[]
}

export interface SetMembersRequest {
    memberIds?: number[]
    /** Whether members in another group of the same set are moved out of it, rather than refused. */
    move?: boolean
}

const groups = createCrudResource<MemberGroup, GroupRequest, GroupRequest, GroupDetail>('/groups')

export const listGroups = groups.list
export const getGroup = groups.get
export const createGroup = groups.create
export const updateGroup = groups.update
export const deleteGroup = groups.remove

export async function getGroupMembers(groupId: number): Promise<StationMember[]> {
    const res = await client.get<StationMember[]>(`/groups/${groupId}/members`)
    return res.data
}

export async function setGroupMembers(groupId: number, data: SetMembersRequest): Promise<StationMember[]> {
    const res = await client.put<StationMember[]>(`/groups/${groupId}/members`, data)
    return res.data
}

export async function getGroupPermissions(groupId: number): Promise<PermissionGrant[]> {
    const res = await client.get<PermissionGrant[]>(`/groups/${groupId}/permissions`)
    return res.data
}

export async function setGroupPermissions(groupId: number, data: { permissionIds: number[] }): Promise<PermissionGrant[]> {
    const res = await client.put<PermissionGrant[]>(`/groups/${groupId}/permissions`, data)
    return res.data
}

export async function getMemberGroups(memberId: number): Promise<MemberGroup[]> {
    const res = await client.get<MemberGroup[]>(`/station-members/${memberId}/groups`)
    return res.data
}

/** Replaces the groups one member is in, in one step, and answers the groups they are in afterwards. */
export async function setMemberGroups(memberId: number, groupIds: number[]): Promise<MemberGroup[]> {
    const res = await client.put<MemberGroup[]>(`/station-members/${memberId}/groups`, {groupIds})
    return res.data
}

export async function convertToTag(groupId: number): Promise<void> {
    await client.post(`/groups/${groupId}/convert-to-tag`)
}
