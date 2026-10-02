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
    MemberWithName,
    Permission,
    SetGroupPermissionsRequest,
    SetMembersRequest,
} from './generated/schema'

const groups = createCrudResource<MemberGroup, GroupRequest, GroupRequest, GroupDetail>('/groups')

export const listGroups = groups.list
export const getGroup = groups.get
export const createGroup = groups.create
export const updateGroup = groups.update
export const deleteGroup = groups.remove

export async function getGroupMembers(groupId: number): Promise<MemberWithName[]> {
    const res = await client.get<MemberWithName[]>(`/groups/${groupId}/members`)
    return res.data
}

export async function setGroupMembers(groupId: number, data: SetMembersRequest): Promise<MemberWithName[]> {
    const res = await client.put<MemberWithName[]>(`/groups/${groupId}/members`, data)
    return res.data
}

export async function getGroupPermissions(groupId: number): Promise<Permission[]> {
    const res = await client.get<Permission[]>(`/groups/${groupId}/permissions`)
    return res.data
}

export async function setGroupPermissions(groupId: number, data: SetGroupPermissionsRequest): Promise<Permission[]> {
    const res = await client.put<Permission[]>(`/groups/${groupId}/permissions`, data)
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
