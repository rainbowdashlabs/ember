/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface TwoFactorStatus {
    enrolled: boolean
    factors: FactorInfo[]
    unusedBackupCodes: number
    webauthnAvailable: boolean
    trustedDeviceMaxDays: number
}

export interface TrustedDevice {
    id: number
    userAgent: string | null
    createdAt: string
    lastSeenAt: string
    trustedUntil: string
    current: boolean
}

export interface FactorInfo {
    id: number
    kind: string
    label: string
    createdAt: string
    lastUsedAt?: string
}

export interface TotpBeginResponse {
    secret: string
    otpauthUri: string
    qrPng: string
    recoveryCodes: string[]
}

export interface Verify2faResponse {
    token: string
    expiresAt: string
}

export async function getTwoFactorStatus(): Promise<TwoFactorStatus> {
    const res = await client.get<TwoFactorStatus>('/account/2fa/status')
    return res.data
}

export async function beginTotpSetup(): Promise<TotpBeginResponse> {
    const res = await client.post<TotpBeginResponse>('/account/2fa/totp/begin')
    return res.data
}

export async function confirmTotpSetup(
    secret: string,
    code: string,
    recoveryCodes: string[],
    password?: string,
): Promise<void> {
    await client.post('/account/2fa/totp/confirm', {secret, code, recoveryCodes, password})
}

export async function removeTotp(): Promise<void> {
    await client.post('/account/2fa/totp/remove')
}

export async function regenerateBackupCodes(): Promise<{ codes: string[] }> {
    const res = await client.post<{ codes: string[] }>('/account/2fa/backup-codes/regenerate')
    return res.data
}

/**
 * @param trustedDevice the box from the login screen, carried through so somebody who ticked it
 *                      does not end up with the short session after the second factor.
 */
export async function verify2fa(
    preAuthToken: string,
    factor: string,
    proof: string,
    rememberDeviceDays?: number,
    trustedDevice?: boolean,
): Promise<Verify2faResponse> {
    const res = await client.post<Verify2faResponse>('/auth/2fa', {
        preAuthToken,
        factor,
        proof,
        rememberDeviceDays,
        trustedDevice,
    })
    return res.data
}

export async function listTrustedDevices(): Promise<TrustedDevice[]> {
    const res = await client.get<{ devices: TrustedDevice[] }>('/account/2fa/trusted-devices')
    return res.data.devices
}

export async function revokeTrustedDevice(id: number): Promise<void> {
    await client.post(`/account/2fa/trusted-devices/${id}/revoke`)
}

export async function revokeAllTrustedDevices(): Promise<void> {
    await client.post('/account/2fa/trusted-devices/revoke-all')
}

export interface StepUpResponse {
    verifiedAt: string
}

export async function stepUp(factor: string, proof: string): Promise<StepUpResponse> {
    const res = await client.post<StepUpResponse>('/auth/2fa/stepup', {factor, proof})
    return res.data
}

// -- WebAuthn --

export interface WebAuthnBeginResponse {
    challengeToken: string
    optionsJson: string
}

export interface WebAuthnRegisterFinishResponse {
    factor: FactorInfo
    recoveryCodes: string[]
}

export async function webauthnRegisterBegin(): Promise<WebAuthnBeginResponse> {
    const res = await client.post<WebAuthnBeginResponse>('/account/2fa/webauthn/register/begin')
    return res.data
}

export async function webauthnRegisterFinish(
    challengeToken: string,
    credentialJson: string,
    label: string,
    password?: string,
): Promise<WebAuthnRegisterFinishResponse> {
    const res = await client.post<WebAuthnRegisterFinishResponse>(
        '/account/2fa/webauthn/register/finish',
        {challengeToken, credentialJson, label, password},
    )
    return res.data
}

export async function webauthnLoginBegin(preAuthToken: string): Promise<WebAuthnBeginResponse> {
    const res = await client.post<WebAuthnBeginResponse>('/auth/2fa/webauthn/begin', {preAuthToken})
    return res.data
}

export async function webauthnLoginFinish(
    preAuthToken: string,
    challengeToken: string,
    credentialJson: string,
    rememberDeviceDays?: number,
): Promise<Verify2faResponse> {
    const res = await client.post<Verify2faResponse>(
        '/auth/2fa/webauthn/finish',
        {preAuthToken, challengeToken, credentialJson, rememberDeviceDays},
    )
    return res.data
}

export async function webauthnStepUpBegin(): Promise<WebAuthnBeginResponse> {
    const res = await client.post<WebAuthnBeginResponse>('/auth/2fa/stepup/webauthn/begin')
    return res.data
}

export async function webauthnStepUpFinish(
    challengeToken: string,
    credentialJson: string,
): Promise<StepUpResponse> {
    const res = await client.post<StepUpResponse>(
        '/auth/2fa/stepup/webauthn/finish',
        {challengeToken, credentialJson},
    )
    return res.data
}

export async function removeFactor(factorId: number): Promise<void> {
    await client.post(`/account/2fa/factors/${factorId}/remove`)
}

export async function renameFactor(factorId: number, label: string): Promise<void> {
    await client.post(`/account/2fa/factors/${factorId}/rename`, {label})
}
