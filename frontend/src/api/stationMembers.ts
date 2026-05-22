/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {CreateMemberRequest, Role, SetManagersRequest, SetRolesRequest, StationMember,} from './types'

export async function listAllRoles(): Promise<Role[]> {
    const res = await client.get<Role[]>('/roles')
    return res.data
}

export async function listMembers(stationId: number, includeFormer = false): Promise<StationMember[]> {
    const params: Record<string, unknown> = {stationId}
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

export async function getRoles(memberId: number): Promise<Role[]> {
    const res = await client.get<Role[]>(`/station-members/${memberId}/roles`)
    return res.data
}

export async function getAllMemberRoles(): Promise<Record<number, Role[]>> {
    const res = await client.get<Record<number, Role[]>>('/station-members/all-roles')
    return res.data
}

export async function setRoles(memberId: number, data: SetRolesRequest): Promise<Role[]> {
    const res = await client.put<Role[]>(`/station-members/${memberId}/roles`, data)
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
