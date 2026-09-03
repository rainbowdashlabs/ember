/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {MessageResponse} from './types'

export interface InviteRequest {
    email?: string
    firstName?: string
    lastName?: string
}

export interface InviteResponse {
    id: number
    email?: string
    firstName?: string
    lastName?: string
}

export interface ResetPasswordRequest {
    accountId?: number
    forceChange?: boolean
}

export async function invite(data: InviteRequest): Promise<InviteResponse> {
    const res = await client.post<InviteResponse>('/members/invite', data)
    return res.data
}

export async function updateAccount(accountId: number, data: {
    email?: string;
    /** The name this account signs in with. Absent leaves it alone; empty clears it. */
    username?: string;
    firstName?: string;
    lastName?: string
}): Promise<MessageResponse> {
    const res = await client.put<MessageResponse>(`/members/${accountId}`, data)
    return res.data
}

export async function resetPassword(data: ResetPasswordRequest): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/members/reset-password', data)
    return res.data
}

export interface OnboardAgainResult {
    /** Whether a setup mail could go out; when not, the QR code in the room is the way. */
    mailed: boolean
}

/**
 * Onboards a member again: every passkey disabled, every session ended, a fresh setup link where
 * mail about the account already goes.
 */
export async function onboardAgain(accountId: number): Promise<OnboardAgainResult> {
    const res = await client.post<OnboardAgainResult>('/members/onboard-again', {accountId})
    return res.data
}

export interface MemberPasskeyCode {
    code: string
    qrPng: string
    expiresAt: string
}

/** The member manager's passkey code, for an addressless member with no guardian to hand it over. */
export async function issuePasskeyCode(accountId: number): Promise<MemberPasskeyCode> {
    const res = await client.post<MemberPasskeyCode>('/members/passkey-code', {accountId})
    return res.data
}

export async function revokePasskeyCode(accountId: number): Promise<void> {
    await client.delete(`/members/passkey-code/${accountId}`)
}

// -- Page-editor picker. PAGE_EDIT-gated. --

export interface MemberSearchResult {
    memberUid: string
    displayName: string
    userType: string | null
    displayTag: string | null
    displayTagColor: string | null
    avatarUrl: string | null
}

export async function searchMembers(query?: string, limit = 20): Promise<MemberSearchResult[]> {
    const params: Record<string, string | number> = {limit}
    if (query) params.q = query
    const res = await client.get<MemberSearchResult[]>('/members/search', {params})
    return res.data
}

/** Resolves a single member by its UUID for picker display. Returns {@code null} if not found. */
export async function getMemberPickerByUid(uid: string): Promise<MemberSearchResult | null> {
    const res = await client.get<MemberSearchResult[]>('/members/search', {params: {uid}})
    return res.data[0] ?? null
}
