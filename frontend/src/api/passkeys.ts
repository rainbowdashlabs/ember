/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client, {scheduleTokenRefresh} from './client'
import {isStorageDenied, setItem} from './storage'
import {StorageDeniedError, type LoginResponse} from './auth'
import type {PasskeyModeName} from './adminSettings'

export interface PasskeyCeremony {
    challengeToken: string
    optionsJson: string
}

export interface PasskeyEntry {
    id: number
    label: string
    createdAt: string
    lastUsedAt: string | null
    aaguid: string | null
    /** Whether this passkey has ever completed a sign-in ceremony, the trial included. */
    tried: boolean
    /** URL-safe base64 credential id, for the browser's signal calls. */
    credentialId: string | null
}

export interface PasskeysStatus {
    passkeys: PasskeyEntry[]
    hasPassword: boolean
    passwordLoginEnabled: boolean
    /** Whether the member opted their passkeys into the password path as well. */
    askWithPassword: boolean
    /** Whether the switch that turns password sign-in off may be offered at all. */
    mayDisablePasswordLogin: boolean
    mode: PasskeyModeName
    /** The effective relying-party id, which the browser's signal calls need. */
    rpId: string
    /** URL-safe base64 user handle, or null while the account holds no passkey. */
    userHandle: string | null
}

export type TrialOutcome = 'OK' | 'FOREIGN_CREDENTIAL' | 'FAILED'

// -- The passwordless sign-in --

export async function publicPasskeyMode(): Promise<PasskeyModeName> {
    const res = await client.get<{mode: PasskeyModeName}>('/public/settings/passkeys')
    return res.data.mode
}

export async function passkeySignInBegin(): Promise<PasskeyCeremony> {
    const res = await client.post<PasskeyCeremony>('/auth/passkey/begin')
    return res.data
}

/** Finishes the sign-in and persists the session the way a password login does. */
export async function passkeySignInFinish(
    challengeToken: string,
    credentialJson: string,
    trustedDevice: boolean,
): Promise<LoginResponse> {
    if (isStorageDenied()) {
        throw new StorageDeniedError()
    }
    const res = await client.post<LoginResponse>('/auth/passkey/finish', {
        challengeToken,
        credentialJson,
        trustedDevice,
    })
    if (res.data.token) {
        setItem('session_token', res.data.token)
        if (res.data.expiresAt) {
            setItem('session_expires_at', res.data.expiresAt)
            scheduleTokenRefresh(res.data.expiresAt)
        }
    }
    return res.data
}

// -- The member's own passkeys --

export async function getPasskeysStatus(): Promise<PasskeysStatus> {
    const res = await client.get<PasskeysStatus>('/account/passkeys')
    return res.data
}

export async function passkeyCreateBegin(): Promise<PasskeyCeremony> {
    const res = await client.post<PasskeyCeremony>('/account/passkeys/begin')
    return res.data
}

export async function passkeyCreateFinish(
    challengeToken: string,
    credentialJson: string,
    label: string,
): Promise<PasskeyEntry> {
    const res = await client.post<PasskeyEntry>('/account/passkeys/finish', {challengeToken, credentialJson, label})
    return res.data
}

export async function renamePasskey(id: number, label: string): Promise<void> {
    await client.post(`/account/passkeys/${id}/rename`, {label})
}

export interface PasskeyRemoval {
    /** True when removing the last passkey opened the password door again. */
    passwordLoginReenabled: boolean
}

export async function removePasskey(id: number): Promise<PasskeyRemoval> {
    const res = await client.delete<PasskeyRemoval>(`/account/passkeys/${id}`)
    return res.data
}

export async function setPasswordLogin(enabled: boolean): Promise<void> {
    await client.post('/account/passkeys/password-login', {enabled})
}

export async function setAskWithPassword(enabled: boolean): Promise<void> {
    await client.post('/account/passkeys/second-factor', {enabled})
}

// -- The offer --

export async function offerState(): Promise<boolean> {
    const res = await client.get<{offer: boolean}>('/account/passkeys/offer')
    return res.data.offer
}

export async function answerOffer(answer: 'LATER' | 'DECLINED'): Promise<void> {
    await client.post('/account/passkeys/offer-answer', {answer})
}

// -- The trial that follows a creation --

export async function trialBegin(): Promise<PasskeyCeremony> {
    const res = await client.post<PasskeyCeremony>('/account/passkeys/trial/begin')
    return res.data
}

export async function trialFinish(challengeToken: string, credentialJson: string): Promise<TrialOutcome> {
    const res = await client.post<{outcome: TrialOutcome}>('/account/passkeys/trial/finish', {
        challengeToken,
        credentialJson,
    })
    return res.data.outcome
}
