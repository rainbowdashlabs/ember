/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    ManagedAccess,
    ManagedMember,
    MemberProfile,
    MemberRequirement,
    MergedValue,
    MyInventoryItem,
    PasskeyCodeResponse,
} from './generated/schema'

export async function listManaged(): Promise<ManagedMember[]> {
    const res = await client.get<ManagedMember[]>('/managed-members')
    return res.data
}

export async function getProfile(memberId: number): Promise<MemberProfile> {
    const res = await client.get<MemberProfile>(`/managed-members/${memberId}/profile`)
    return res.data
}

export async function setProfile(memberId: number, values: {
    fieldId: number;
    value: string
}[]): Promise<MergedValue[]> {
    const res = await client.put<MergedValue[]>(`/managed-members/${memberId}/profile`, {values})
    return res.data
}

/** A member's gear as they read it themselves, with the movement each piece is on and its picture. */
export async function getMemberInventory(memberId: number): Promise<MyInventoryItem[]> {
    const res = await client.get<MyInventoryItem[]>(`/managed-members/${memberId}/inventory-items`)
    return res.data
}

export async function getMemberRequirements(memberId: number): Promise<MemberRequirement[]> {
    const res = await client.get<MemberRequirement[]>(`/managed-members/${memberId}/inventory-requirements`)
    return res.data
}

/** The access a guardian manages for a member in their care. */
export async function getAccess(memberId: number): Promise<ManagedAccess> {
    const res = await client.get<ManagedAccess>(`/managed-members/${memberId}/access`)
    return res.data
}

export async function setEmail(memberId: number, email: string): Promise<ManagedAccess> {
    const res = await client.put<ManagedAccess>(`/managed-members/${memberId}/email`, {email})
    return res.data
}

export async function setUsername(memberId: number, username: string): Promise<ManagedAccess> {
    const res = await client.put<ManagedAccess>(`/managed-members/${memberId}/username`, {username})
    return res.data
}

/**
 * Sets the password of a member in the guardian's care. Only accepted for a member with no address
 * of their own, whose invitation would land in the guardian's postbox anyway.
 */
export async function setPassword(memberId: number, password: string): Promise<ManagedAccess> {
    const res = await client.put<ManagedAccess>(`/managed-members/${memberId}/password`, {password})
    return res.data
}

/** The QR code held up in the room: lets the member create a passkey on their own device. */
export async function issuePasskeyCode(memberId: number): Promise<PasskeyCodeResponse> {
    const res = await client.post<PasskeyCodeResponse>(`/managed-members/${memberId}/passkey-code`)
    return res.data
}

/** Kills the open code when the guardian leaves the screen. */
export async function revokePasskeyCode(memberId: number): Promise<void> {
    await client.delete(`/managed-members/${memberId}/passkey-code`)
}

export async function setLogin(memberId: number, enabled: boolean): Promise<ManagedAccess> {
    const res = await client.put<ManagedAccess>(`/managed-members/${memberId}/login`, {enabled})
    return res.data
}
