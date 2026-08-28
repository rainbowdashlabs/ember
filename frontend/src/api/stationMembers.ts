/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {MemberIdentity, PermissionGrant, StationMember} from './types'

export interface CreateMemberRequest {
    stationId?: number
    accountId?: number
}

export interface SetRolesRequest {
    roleIds?: number[]
}

export interface SetPermissionsRequest {
    permissions?: string[]
}

export interface SetUserTypeRequest {
    userType?: string
}

export interface SetManagersRequest {
    managerIds?: number[]
}

export async function listAllPermissions(): Promise<PermissionGrant[]> {
    const res = await client.get<PermissionGrant[]>('/permissions')
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

export async function listCompletions(restriction?: { type: string; entityId: number }): Promise<MemberCompletion[]> {
    const params: Record<string, string> = {}
    if (restriction) {
        params.restrictionType = restriction.type
        params.entityId = String(restriction.entityId)
    }
    const res = await client.get<MemberCompletion[]>('/station-members/completions', { params })
    return res.data
}

export interface RichMember {
    id: number
    stationId: number
    accountId: number | null
    name: string
    email: string
    accountSetupPending: boolean
    setupMailExpiresAt: string | null
    /**
     * Whether anything written to this member would reach anybody: their own address where it is a
     * real one, a guardian's where it is not.
     */
    mailReachable: boolean
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

export async function resendSetupMail(memberId: number): Promise<void> {
    await client.post(`/station-members/${memberId}/resend-setup-mail`)
}

const members = createCrudResource<StationMember, CreateMemberRequest>('/station-members')

export async function listMembers(includeFormer = false): Promise<StationMember[]> {
    return members.list({includeFormer: includeFormer || undefined})
}

export const getMember = members.get
export const createMember = members.create
export const deleteMember = members.remove

export async function getPermissions(memberId: number): Promise<PermissionGrant[]> {
    const res = await client.get<PermissionGrant[]>(`/station-members/${memberId}/permissions`)
    return res.data
}

export async function getAllMemberRoles(): Promise<Record<number, PermissionGrant[]>> {
    const res = await client.get<Record<number, PermissionGrant[]>>('/station-members/all-permissions')
    return res.data
}

export async function setPermissions(memberId: number, data: { permissionIds: number[] }): Promise<PermissionGrant[]> {
    const res = await client.put<PermissionGrant[]>(`/station-members/${memberId}/permissions`, data)
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

export async function setManaged(memberId: number, managedIds: number[]): Promise<StationMember[]> {
    const res = await client.put<StationMember[]>(`/station-members/${memberId}/managed`, { managedIds })
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

export async function setUserType(memberId: number, userType: string): Promise<void> {
    await client.put(`/station-members/${memberId}/user-type`, { userType })
}

export async function setJoinDate(memberId: number, joinDate: string): Promise<void> {
    await client.put(`/station-members/${memberId}/join-date`, { joinDate })
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
