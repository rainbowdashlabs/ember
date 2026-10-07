/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    InviteRequest,
    IssuedOneTimePassword,
    MemberCard,
    MemberInviteResponse,
    MemberSearchResult,
    MessageResponse,
    NameChangeView,
    OnboardAgainResponse,
    OwnNameChange,
    OwnNameChangeResponse,
    PasskeyCodeResponse,
    ResetPasswordRequest,
    UpdateAccountResponse,
} from './generated/schema'

export async function invite(data: InviteRequest): Promise<MemberInviteResponse> {
    const res = await client.post<MemberInviteResponse>('/members/invite', data)
    return res.data
}

/**
 * Changes an account's address, sign-in name and register name.
 *
 * <p>The answer's `emailChange` says what became of an address given in the same call: null when
 * the address was left alone, COMMITTED when it is already the account's, WAITING when a link still
 * has to be clicked.
 */
export async function updateAccount(accountId: number, data: {
    email?: string;
    /** The name this account signs in with. Absent leaves it alone; empty clears it. */
    username?: string;
    firstName?: string;
    lastName?: string
}): Promise<UpdateAccountResponse> {
    const res = await client.put<UpdateAccountResponse>(`/members/${accountId}`, data)
    return res.data
}

/**
 * Sets the name a member is called by at their station.
 *
 * A member may set their own and whoever looks after them may set theirs. Anybody else is refused,
 * however senior: what somebody is called is theirs to decide.
 *
 * @param nickname the name, or null and empty alike to go back to the register name
 */
export async function setNickname(memberId: number, nickname: string | null): Promise<void> {
    await client.put(`/station-members/${memberId}/nickname`, {nickname})
}

export async function resetPassword(data: ResetPasswordRequest): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/members/reset-password', data)
    return res.data
}

/**
 * Onboards a member again: every passkey disabled, every session ended, a fresh setup link where
 * mail about the account already goes. The answer says whether a setup mail could go out; when not,
 * the QR code in the room is the way.
 */
export async function onboardAgain(accountId: number): Promise<OnboardAgainResponse> {
    const res = await client.post<OnboardAgainResponse>('/members/onboard-again', {accountId})
    return res.data
}

/** The member manager's passkey code, for an addressless member with no guardian to hand it over. */
export async function issuePasskeyCode(accountId: number): Promise<PasskeyCodeResponse> {
    const res = await client.post<PasskeyCodeResponse>('/members/passkey-code', {accountId})
    return res.data
}

/**
 * A one-time password for a member whose account belongs to this station alone. It replaces their
 * password, ends their sessions and is answered only this once.
 */
export async function issueOneTimePassword(accountId: number): Promise<IssuedOneTimePassword> {
    const res = await client.post<IssuedOneTimePassword>(`/members/${accountId}/one-time-password`)
    return res.data
}

export async function revokePasskeyCode(accountId: number): Promise<void> {
    await client.delete(`/members/passkey-code/${accountId}`)
}

export async function searchMembers(query?: string, limit = 20): Promise<MemberSearchResult[]> {
    const params: Record<string, string | number> = {limit}
    if (query) params.q = query
    const res = await client.get<MemberSearchResult[]>('/members/search', {params})
    return res.data
}

/** The short card shown when somebody looks at the name of a member of their own station. */
export async function getMemberCard(memberUid: string): Promise<MemberCard> {
    const res = await client.get<MemberCard>(`/station-members/by-uid/${memberUid}/card`)
    return res.data
}

/** The new names the station's members asked for and that wait for somebody who confirms member changes. */
export async function listNameChangeRequests(): Promise<NameChangeView[]> {
    const res = await client.get<NameChangeView[]>('/name-change-requests')
    return res.data
}

/** Approves a name request; the member's account takes the new name. */
export async function approveNameChange(requestId: number): Promise<void> {
    await client.post(`/name-change-requests/${requestId}/approve`)
}

/** Turns a name request down, with a reason the member is shown, or none. */
export async function denyNameChange(requestId: number, reason: string | null): Promise<void> {
    await client.post(`/name-change-requests/${requestId}/deny`, {reason})
}

/** The new name the reader asked for and that still waits, or null. */
export async function getOwnNameChange(): Promise<OwnNameChange | null> {
    const res = await client.get<OwnNameChangeResponse>('/account/name-change-request')
    return res.data.pending
}

/** Takes back the new name the reader asked for. */
export async function withdrawOwnNameChange(): Promise<void> {
    await client.delete('/account/name-change-request')
}

/** Resolves a single member by its UUID for picker display. Returns {@code null} if not found. */
export async function getMemberPickerByUid(uid: string): Promise<MemberSearchResult | null> {
    const res = await client.get<MemberSearchResult[]>('/members/search', {params: {uid}})
    return res.data[0] ?? null
}
