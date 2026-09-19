/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface TwoFactorPolicy {
    id: number
    scope: 'INSTANCE' | 'STATION'
    stationId: number | null
    userType: string | null
    required: boolean
    graceDays: number
    createdBy: number | null
    createdAt: string
}

export interface MemberStatus {
    memberId: number
    accountId: number
    firstName: string
    lastName: string
    email: string
    userType: string
    enrolled: boolean
    mandated: boolean
}

/** Everything the two-factor audit log records. */
export const TwoFactorEvent = {
    ENROLLED: 'ENROLLED',
    REMOVED: 'REMOVED',
    LOGIN_VERIFIED: 'LOGIN_VERIFIED',
    STEPUP_VERIFIED: 'STEPUP_VERIFIED',
    BACKUP_CODE_USED: 'BACKUP_CODE_USED',
    BACKUP_CODE_REGENERATED: 'BACKUP_CODE_REGENERATED',
    ADMIN_RESET: 'ADMIN_RESET',
    TRUSTED_DEVICE_ADDED: 'TRUSTED_DEVICE_ADDED',
    TRUSTED_DEVICE_REVOKED: 'TRUSTED_DEVICE_REVOKED',
    POLICY_CHANGED: 'POLICY_CHANGED',
    PASSKEY_SIGN_IN: 'PASSKEY_SIGN_IN',
    PASSKEY_ENROLLED_VIA_DEVICE_CODE: 'PASSKEY_ENROLLED_VIA_DEVICE_CODE',
    PASSKEY_CODE_ISSUED: 'PASSKEY_CODE_ISSUED',
    PASSWORD_LOGIN_DISABLED: 'PASSWORD_LOGIN_DISABLED',
    PASSWORD_LOGIN_ENABLED: 'PASSWORD_LOGIN_ENABLED',
    PASSWORD_RETIRED: 'PASSWORD_RETIRED',
    STEPUP_FAILED: 'STEPUP_FAILED',
    SIGNED_IN_VIA_DEVICE_CODE: 'SIGNED_IN_VIA_DEVICE_CODE',
    STEPUP_VIA_DEVICE_CODE: 'STEPUP_VIA_DEVICE_CODE',
    DEVICE_REQUEST_APPROVED: 'DEVICE_REQUEST_APPROVED',
} as const

export type TwoFactorEventName = (typeof TwoFactorEvent)[keyof typeof TwoFactorEvent]

/** The second factors an account can hold. */
export const TwoFactorKind = {
    TOTP: 'TOTP',
    WEBAUTHN: 'WEBAUTHN',
    BACKUP_CODES: 'BACKUP_CODES',
} as const

export type TwoFactorKindName = (typeof TwoFactorKind)[keyof typeof TwoFactorKind]

export interface AuditEntry {
    id: number
    accountId: number
    actorId: number | null
    event: TwoFactorEventName
    factorKind: TwoFactorKindName | null
    userAgent: string | null
    country: string | null
    createdAt: string
}

export async function listStationPolicies(): Promise<TwoFactorPolicy[]> {
    const res = await client.get<{ policies: TwoFactorPolicy[] }>('/station/2fa/policies')
    return res.data.policies
}

export async function upsertStationPolicy(
    userType: string,
    required: boolean,
    graceDays?: number,
): Promise<TwoFactorPolicy> {
    const res = await client.put<TwoFactorPolicy>('/station/2fa/policies', {userType, required, graceDays})
    return res.data
}

export async function deleteStationPolicy(id: number): Promise<void> {
    await client.delete(`/station/2fa/policies/${id}`)
}

export async function listStationMemberStatus(): Promise<MemberStatus[]> {
    const res = await client.get<{ members: MemberStatus[] }>('/station/2fa/members')
    return res.data.members
}

export async function listAssignableUserTypes(): Promise<string[]> {
    const res = await client.get<{ userTypes: string[] }>('/station/2fa/user-types')
    return res.data.userTypes
}

export async function listInstancePolicies(): Promise<TwoFactorPolicy[]> {
    const res = await client.get<{ policies: TwoFactorPolicy[] }>('/admin/2fa/policies')
    return res.data.policies
}

export async function upsertInstancePolicy(
    userType: string,
    required: boolean,
    graceDays?: number,
): Promise<TwoFactorPolicy> {
    const res = await client.put<TwoFactorPolicy>('/admin/2fa/policies', {userType, required, graceDays})
    return res.data
}

export async function deleteInstancePolicy(id: number): Promise<void> {
    await client.delete(`/admin/2fa/policies/${id}`)
}

export async function listAuditLog(
    params: { accountId?: number; limit?: number; offset?: number } = {},
): Promise<AuditEntry[]> {
    const res = await client.get<{ entries: AuditEntry[] }>('/admin/2fa/audit', {params})
    return res.data.entries
}

export async function resetAccount2FAByInstanceAdmin(accountId: number): Promise<void> {
    await client.post(`/admin/accounts/${accountId}/2fa/reset`)
}

export async function resetAccount2FAByStationAdmin(accountId: number): Promise<void> {
    await client.post(`/station/accounts/${accountId}/2fa/reset`)
}

export interface AccountSearchResult {
    id: number
    uid: string
    displayName: string
    firstName: string | null
    lastName: string | null
    email: string
}

export async function searchAccounts(query?: string, limit = 20): Promise<AccountSearchResult[]> {
    const params: Record<string, string | number> = {limit}
    if (query) params.q = query
    const res = await client.get<AccountSearchResult[]>('/admin/accounts/search', {params})
    return res.data
}

export async function getAccountPickerByUid(uid: string): Promise<AccountSearchResult | null> {
    const res = await client.get<AccountSearchResult[]>('/admin/accounts/search', {params: {uid}})
    return res.data[0] ?? null
}
