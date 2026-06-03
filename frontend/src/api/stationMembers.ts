/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {CreateMemberRequest, MemberIdentity, PermissionGrant, SetManagersRequest, SetRolesRequest, StationMember,} from './types'

export async function listAllRoles(): Promise<PermissionGrant[]> {
    const res = await client.get<PermissionGrant[]>('/roles')
    return res.data
}

export interface MemberCompletion {
    id: number
    name: string
    stationUid: string
    memberUid: string
    stationName?: string | null
    nameColor?: string | null
    displayTag?: { name: string; color: string } | null
}

export async function listCompletions(): Promise<MemberCompletion[]> {
    const res = await client.get<MemberCompletion[]>('/station-members/completions')
    return res.data
}

export interface RichMember {
    id: number
    stationId: number
    accountId: number | null
    name: string
    email: string
    former: boolean
    userType: string
    roles: string[]
    groups: { id: number; name: string }[]
    tags: { id: number; name: string }[]
    profileValues: Record<string, unknown>
    identity?: MemberIdentity | null
}

export async function listRichMembers(includeFormer = false): Promise<RichMember[]> {
    const params: Record<string, unknown> = {}
    if (includeFormer) params.includeFormer = true
    const res = await client.get<RichMember[]>('/station-members/rich', { params })
    return res.data
}

export async function listMembers(includeFormer = false): Promise<StationMember[]> {
    const params: Record<string, unknown> = {}
    if (includeFormer) params.includeFormer = true
    const res = await client.get<StationMember[]>('/station-members', {params})
    return res.data
}

export async function getMember(id: number): Promise<StationMember> {
    const res = await client.get<StationMember>(`/station-members/${id}`)
    return res.data
}

export async function createMember(data: CreateMemberRequest): Promise<StationMember> {
    const res = await client.post<StationMember>('/station-members', data)
    return res.data
}

export async function deleteMember(id: number): Promise<void> {
    await client.delete(`/station-members/${id}`)
}

export async function getPermissions(memberId: number): Promise<PermissionGrant[]> {
    const res = await client.get<PermissionGrant[]>(`/station-members/${memberId}/roles`)
    return res.data
}

export async function getAllMemberRoles(): Promise<Record<number, PermissionGrant[]>> {
    const res = await client.get<Record<number, PermissionGrant[]>>('/station-members/all-roles')
    return res.data
}

export async function setPermissions(memberId: number, data: SetRolesRequest): Promise<PermissionGrant[]> {
    const res = await client.put<PermissionGrant[]>(`/station-members/${memberId}/roles`, data)
    return res.data
}

export async function getManaged(memberId: number): Promise<StationMember[]> {
    const res = await client.get<StationMember[]>(`/station-members/${memberId}/managed`)
    return res.data
}

export async function getManagers(memberId: number): Promise<StationMember[]> {
    const res = await client.get<StationMember[]>(`/station-members/${memberId}/managers`)
    return res.data
}

export async function setManagers(memberId: number, data: SetManagersRequest): Promise<StationMember[]> {
    const res = await client.put<StationMember[]>(`/station-members/${memberId}/managers`, data)
    return res.data
}

export async function listFormerMembers(): Promise<StationMember[]> {
    const res = await client.get<StationMember[]>('/station-members/former')
    return res.data
}

export async function markFormer(memberId: number): Promise<void> {
    await client.post(`/station-members/${memberId}/mark-former`)
}

export async function reactivateMember(memberId: number): Promise<void> {
    await client.post(`/station-members/${memberId}/reactivate`)
}

export async function getUserTypePermissions(userType: string): Promise<PermissionGrant[]> {
    const res = await client.get<PermissionGrant[]>(`/user-type-permissions/${userType}`)
    return res.data
}

export async function getEffectiveUserTypePermissions(userType: string): Promise<string[]> {
    const res = await client.get<string[]>(`/user-type-permissions/${userType}/effective`)
    return res.data
}

export async function setUserTypePermissions(userType: string, permissionIds: number[]): Promise<PermissionGrant[]> {
    const res = await client.put<PermissionGrant[]>(`/user-type-permissions/${userType}`, { permissionIds })
    return res.data
}
