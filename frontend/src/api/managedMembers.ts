/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {ProfileField, ProfileFieldValue} from './profileFields'
import type {MyInventoryItem, MyRequirement} from './inventory'

export interface ManagedMember {
    id: number
    stationId: string
    accountId: number
    name: string
    email: string
}

export async function listManaged(): Promise<ManagedMember[]> {
    const res = await client.get<ManagedMember[]>('/managed-members')
    return res.data
}

export interface MemberProfile {
    fields: ProfileField[]
    values: ProfileFieldValue[]
}

export async function getProfile(memberId: number): Promise<MemberProfile> {
    const res = await client.get<MemberProfile>(`/managed-members/${memberId}/profile`)
    return res.data
}

export async function setProfile(memberId: number, values: {
    fieldId: number;
    value: string
}[]): Promise<ProfileFieldValue[]> {
    const res = await client.put<ProfileFieldValue[]>(`/managed-members/${memberId}/profile`, {values})
    return res.data
}

export async function getMemberInventory(memberId: number): Promise<MyInventoryItem[]> {
    const res = await client.get<MyInventoryItem[]>(`/managed-members/${memberId}/inventory-items`)
    return res.data
}

export async function getMemberRequirements(memberId: number): Promise<MyRequirement[]> {
    const res = await client.get<MyRequirement[]>(`/managed-members/${memberId}/inventory-requirements`)
    return res.data
}

/** The access a guardian manages for a member in their care. */
export interface ManagedAccess {
    /** The address the account is reached at, or null while it carries only a synthetic one. */
    email: string | null
    /** Whether the member may sign in. */
    loginEnabled: boolean
    /** Whether signing in can be switched on at all, which needs a real address. */
    canSignIn: boolean
}

export async function getAccess(memberId: number): Promise<ManagedAccess> {
    const res = await client.get<ManagedAccess>(`/managed-members/${memberId}/access`)
    return res.data
}

export async function setEmail(memberId: number, email: string): Promise<ManagedAccess> {
    const res = await client.put<ManagedAccess>(`/managed-members/${memberId}/email`, {email})
    return res.data
}

export async function setLogin(memberId: number, enabled: boolean): Promise<ManagedAccess> {
    const res = await client.put<ManagedAccess>(`/managed-members/${memberId}/login`, {enabled})
    return res.data
}
