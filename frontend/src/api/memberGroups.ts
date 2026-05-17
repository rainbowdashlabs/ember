/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    GroupDetail,
    GroupRequest,
    MemberGroup,
    Role,
    SetMembersRequest,
    SetRolesRequest,
    StationMember,
} from './types'

export async function listGroups(): Promise<MemberGroup[]> {
    const res = await client.get<MemberGroup[]>('/groups')
    return res.data
}

export async function getGroup(id: number): Promise<GroupDetail> {
    const res = await client.get<GroupDetail>(`/groups/${id}`)
    return res.data
}

export async function createGroup(data: GroupRequest): Promise<MemberGroup> {
    const res = await client.post<MemberGroup>('/groups', data)
    return res.data
}

export async function updateGroup(id: number, data: GroupRequest): Promise<MemberGroup> {
    const res = await client.put<MemberGroup>(`/groups/${id}`, data)
    return res.data
}

export async function deleteGroup(id: number): Promise<void> {
    await client.delete(`/groups/${id}`)
}

export async function getGroupMembers(groupId: number): Promise<StationMember[]> {
    const res = await client.get<StationMember[]>(`/groups/${groupId}/members`)
    return res.data
}

export async function setGroupMembers(groupId: number, data: SetMembersRequest): Promise<StationMember[]> {
    const res = await client.put<StationMember[]>(`/groups/${groupId}/members`, data)
    return res.data
}

export async function getGroupRoles(groupId: number): Promise<Role[]> {
    const res = await client.get<Role[]>(`/groups/${groupId}/roles`)
    return res.data
}

export async function setGroupRoles(groupId: number, data: SetRolesRequest): Promise<Role[]> {
    const res = await client.put<Role[]>(`/groups/${groupId}/roles`, data)
    return res.data
}

export async function getMemberGroups(memberId: number): Promise<MemberGroup[]> {
    const res = await client.get<MemberGroup[]>(`/station-members/${memberId}/groups`)
    return res.data
}

export async function convertToTag(groupId: number): Promise<void> {
    await client.post(`/groups/${groupId}/convert-to-tag`)
}
