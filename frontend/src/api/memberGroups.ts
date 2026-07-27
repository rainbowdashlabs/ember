/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {
    GroupDetail,
    GroupRequest,
    MemberGroup,
    PermissionGrant,
    SetMembersRequest,
    StationMember,
} from './types'

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

export async function convertToTag(groupId: number): Promise<void> {
    await client.post(`/groups/${groupId}/convert-to-tag`)
}
