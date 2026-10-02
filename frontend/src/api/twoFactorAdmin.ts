/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    AccountSearchResult,
    AuditResponse,
    components,
    MemberStatus,
    MemberStatusResponse,
    PoliciesResponse,
    StationUserType,
    TwoFactorAuditEntry,
    TwoFactorPolicyEntry,
    UpsertPolicyRequest,
    UserTypesResponse,
} from './generated/schema'

type Schemas = components['schemas']

export type TwoFactorEventName = Schemas['TwoFactorEvent']

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
    ONE_TIME_PASSWORD_ISSUED: 'ONE_TIME_PASSWORD_ISSUED',
} as const satisfies Record<TwoFactorEventName, TwoFactorEventName>

export type TwoFactorKindName = Schemas['TwoFactorKind']

/** The second factors an account can hold. */
export const TwoFactorKind = {
    TOTP: 'TOTP',
    WEBAUTHN: 'WEBAUTHN',
    BACKUP_CODES: 'BACKUP_CODES',
} as const satisfies Record<TwoFactorKindName, TwoFactorKindName>

export async function listStationPolicies(): Promise<TwoFactorPolicyEntry[]> {
    const res = await client.get<PoliciesResponse>('/station/2fa/policies')
    return res.data.policies
}

export async function upsertStationPolicy(
    userType: StationUserType,
    required: boolean,
    graceDays?: number,
): Promise<TwoFactorPolicyEntry> {
    const res = await client.put<TwoFactorPolicyEntry>(
        '/station/2fa/policies',
        {userType, required, graceDays} satisfies UpsertPolicyRequest,
    )
    return res.data
}

export async function deleteStationPolicy(id: number): Promise<void> {
    await client.delete(`/station/2fa/policies/${id}`)
}

export async function listStationMemberStatus(): Promise<MemberStatus[]> {
    const res = await client.get<MemberStatusResponse>('/station/2fa/members')
    return res.data.members
}

export async function listAssignableUserTypes(): Promise<StationUserType[]> {
    const res = await client.get<UserTypesResponse>('/station/2fa/user-types')
    return res.data.userTypes
}

export async function listInstancePolicies(): Promise<TwoFactorPolicyEntry[]> {
    const res = await client.get<PoliciesResponse>('/admin/2fa/policies')
    return res.data.policies
}

export async function upsertInstancePolicy(
    userType: StationUserType,
    required: boolean,
    graceDays?: number,
): Promise<TwoFactorPolicyEntry> {
    const res = await client.put<TwoFactorPolicyEntry>(
        '/admin/2fa/policies',
        {userType, required, graceDays} satisfies UpsertPolicyRequest,
    )
    return res.data
}

export async function deleteInstancePolicy(id: number): Promise<void> {
    await client.delete(`/admin/2fa/policies/${id}`)
}

export async function listAuditLog(
    params: { accountId?: number; limit?: number; offset?: number } = {},
): Promise<TwoFactorAuditEntry[]> {
    const res = await client.get<AuditResponse>('/admin/2fa/audit', {params})
    return res.data.entries
}

export async function resetAccount2FAByInstanceAdmin(accountId: number): Promise<void> {
    await client.post(`/admin/accounts/${accountId}/2fa/reset`)
}

export async function resetAccount2FAByStationAdmin(accountId: number): Promise<void> {
    await client.post(`/station/accounts/${accountId}/2fa/reset`)
}

/** An account's name, with its address where it has one: a member in somebody's care may have none. */
export function accountLabel(account: AccountSearchResult): string {
    return account.email ? `${account.displayName} (${account.email})` : account.displayName
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
