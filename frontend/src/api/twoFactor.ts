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

export async function confirmTotpSetup(secret: string, code: string, recoveryCodes: string[]): Promise<void> {
    await client.post('/account/2fa/totp/confirm', {secret, code, recoveryCodes})
}

export async function removeTotp(): Promise<void> {
    await client.post('/account/2fa/totp/remove')
}

export async function regenerateBackupCodes(): Promise<{ codes: string[] }> {
    const res = await client.post<{ codes: string[] }>('/account/2fa/backup-codes/regenerate')
    return res.data
}

export async function verify2fa(preAuthToken: string, factor: string, proof: string): Promise<Verify2faResponse> {
    const res = await client.post<Verify2faResponse>('/auth/2fa', {preAuthToken, factor, proof})
    return res.data
}

export interface StepUpResponse {
    verifiedAt: string
}

export async function stepUp(factor: string, proof: string): Promise<StepUpResponse> {
    const res = await client.post<StepUpResponse>('/auth/2fa/stepup', {factor, proof})
    return res.data
}
