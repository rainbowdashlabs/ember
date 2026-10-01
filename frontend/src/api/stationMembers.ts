/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {
    CreateMemberRequest,
    FormerCheckResponse,
    MemberCompletion,
    MemberGroup,
    MemberWithName,
    Permission,
    PermissionsByMember,
    RichMember,
    SetManagersRequest,
    UserTypeChangeResponse,
} from './generated/schema'

export async function listAllPermissions(): Promise<Permission[]> {
    const res = await client.get<Permission[]>('/permissions')
    return res.data
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

export async function listRichMembers(includeFormer = false): Promise<RichMember[]> {
    const params: Record<string, unknown> = {}
    if (includeFormer) params.includeFormer = true
    const res = await client.get<RichMember[]>('/station-members/rich', { params })
    return res.data
}

export async function resendSetupMail(memberId: number): Promise<void> {
    await client.post(`/station-members/${memberId}/resend-setup-mail`)
}

const members = createCrudResource<MemberWithName, CreateMemberRequest>('/station-members')

export async function listMembers(includeFormer = false): Promise<MemberWithName[]> {
    return members.list({includeFormer: includeFormer || undefined})
}

export const getMember = members.get
export const createMember = members.create
export const deleteMember = members.remove

/**
 * The member of this station carrying that UUID, or {@code null} where nobody does.
 *
 * <p>The member menus name a person by their UUID while the screens below them work from the row id.
 * A screen that already holds the member list can translate the one into the other itself; one whose
 * list was loaded before the person existed cannot, and asking here costs a single row rather than
 * the whole list again.
 */
export async function getMemberByUid(memberUid: string): Promise<MemberWithName | null> {
    try {
        const res = await client.get<MemberWithName>(`/station-members/by-uid/${memberUid}`)
        return res.data
    } catch {
        return null
    }
}

export async function getPermissions(memberId: number): Promise<Permission[]> {
    const res = await client.get<Permission[]>(`/station-members/${memberId}/permissions`)
    return res.data
}

export async function getAllMemberRoles(): Promise<PermissionsByMember> {
    const res = await client.get<PermissionsByMember>('/station-members/all-permissions')
    return res.data
}

export async function setPermissions(memberId: number, data: { permissionIds: number[] }): Promise<Permission[]> {
    const res = await client.put<Permission[]>(`/station-members/${memberId}/permissions`, data)
    return res.data
}

export async function getManaged(memberId: number): Promise<MemberWithName[]> {
    const res = await client.get<MemberWithName[]>(`/station-members/${memberId}/managed`)
    return res.data
}

export async function getManagers(memberId: number): Promise<MemberWithName[]> {
    const res = await client.get<MemberWithName[]>(`/station-members/${memberId}/managers`)
    return res.data
}

export async function setManagers(memberId: number, data: SetManagersRequest): Promise<MemberWithName[]> {
    const res = await client.put<MemberWithName[]>(`/station-members/${memberId}/managers`, data)
    return res.data
}

export async function setManaged(memberId: number, managedIds: number[]): Promise<MemberWithName[]> {
    const res = await client.put<MemberWithName[]>(`/station-members/${memberId}/managed`, { managedIds })
    return res.data
}

export async function listFormerMembers(): Promise<MemberWithName[]> {
    const res = await client.get<MemberWithName[]>('/station-members/former')
    return res.data
}

export async function markFormer(memberId: number): Promise<FormerCheckResponse> {
    const res = await client.post<FormerCheckResponse>(`/station-members/${memberId}/mark-former`)
    return res.data
}

export async function reactivateMember(memberId: number): Promise<FormerCheckResponse> {
    const res = await client.post<FormerCheckResponse>(`/station-members/${memberId}/reactivate`)
    return res.data
}

export async function setUserType(memberId: number, userType: string): Promise<UserTypeChangeResponse> {
    const res = await client.put<UserTypeChangeResponse>(`/station-members/${memberId}/user-type`, { userType })
    return res.data
}

/** The groups a member would leave on becoming the given type, asked before the change is made. */
export async function getUserTypeConsequences(memberId: number, userType: string): Promise<MemberGroup[]> {
    const res = await client.get<MemberGroup[]>(`/station-members/${memberId}/user-type/${userType}/consequences`)
    return res.data
}

export async function setJoinDate(memberId: number, joinDate: string): Promise<void> {
    await client.put(`/station-members/${memberId}/join-date`, { joinDate })
}

export async function getUserTypePermissions(userType: string): Promise<Permission[]> {
    const res = await client.get<Permission[]>(`/user-type-permissions/${userType}`)
    return res.data
}

export async function getEffectiveUserTypePermissions(userType: string): Promise<string[]> {
    const res = await client.get<string[]>(`/user-type-permissions/${userType}/effective`)
    return res.data
}

export async function setUserTypePermissions(userType: string, permissionIds: number[]): Promise<Permission[]> {
    const res = await client.put<Permission[]>(`/user-type-permissions/${userType}`, { permissionIds })
    return res.data
}
