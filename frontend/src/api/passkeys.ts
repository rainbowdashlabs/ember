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

// -- The device handshake --

export interface DeviceRequest {
    /** The eight-character code the approving member types, ungrouped. */
    code: string
    pollSecret: string
    expiresAt: string
    /** Base64 PNG of a QR code opening the approval screen. It does not carry the code. */
    qrPng: string
}

export type DevicePollStatus = 'PENDING' | 'APPROVED' | 'EXPIRED' | 'UNKNOWN'

/**
 * What approving a device request buys. The handshake is the same either way; only what the poll
 * hands over at the end of it differs.
 */
export const DeviceRequestPurpose = {
    ENROL_PASSKEY: 'ENROL_PASSKEY',
    SIGN_IN: 'SIGN_IN',
    STEP_UP: 'STEP_UP',
} as const

export type DeviceRequestPurposeName = (typeof DeviceRequestPurpose)[keyof typeof DeviceRequestPurpose]

export interface DevicePollResult {
    status: DevicePollStatus
    /** Present exactly once: on the poll that found the approval first. */
    enrollToken: string | null
    /** What that token buys. Absent until there is something to claim. */
    purpose: DeviceRequestPurposeName | null
}

/** Somebody the approving reader may sign in: themselves, or a member in their care. */
export interface ApprovalCandidate {
    accountId: number
    name: string
}

export interface DeviceLookup {
    userAgent: string | null
    country: string | null
    createdAt: string
    purpose: DeviceRequestPurposeName
    /** What a step-up was demanded for, absent for the other purposes. */
    stepUpCategory: string | null
    /** The action in a sentence, where the asking side knew one. */
    stepUpOperation: string | null
    /** Whom this reader may sign in, for a sign-in. Themselves first. */
    candidates: ApprovalCandidate[]
}

export async function deviceRequest(): Promise<DeviceRequest> {
    const res = await client.post<DeviceRequest>('/auth/passkey/device-request')
    return res.data
}

/**
 * Asks to be signed in rather than given a credential. Nothing is left on this device, and the
 * instance does not need passkeys switched on at all.
 */
export async function signInRequest(): Promise<DeviceRequest> {
    const res = await client.post<DeviceRequest>('/auth/device/sign-in-request')
    return res.data
}

/**
 * Spends the claim and keeps the session it bought, the way a password or passkey sign-in does.
 * Without persisting it here the device would be signed in on the server and know nothing about it.
 */
export async function signInClaim(claimToken: string): Promise<LoginResponse> {
    if (isStorageDenied()) {
        throw new StorageDeniedError()
    }
    const res = await client.post<LoginResponse>('/auth/device/sign-in-claim', {claimToken})
    if (res.data.token) {
        setItem('session_token', res.data.token)
        if (res.data.expiresAt) {
            setItem('session_expires_at', res.data.expiresAt)
            scheduleTokenRefresh(res.data.expiresAt)
        }
    }
    return res.data
}

export async function devicePoll(pollSecret: string): Promise<DevicePollResult> {
    const res = await client.post<DevicePollResult>('/auth/passkey/device-request/poll', {pollSecret})
    return res.data
}

export async function deviceEnrollBegin(enrollToken: string): Promise<PasskeyCeremony> {
    const res = await client.post<PasskeyCeremony>('/auth/passkey/enroll/begin', {enrollToken})
    return res.data
}

export async function deviceEnrollFinish(
    enrollToken: string,
    challengeToken: string,
    credentialJson: string,
): Promise<void> {
    await client.post('/auth/passkey/enroll/finish', {enrollToken, challengeToken, credentialJson})
}

export async function deviceLookup(code: string): Promise<DeviceLookup> {
    const res = await client.post<DeviceLookup>('/account/passkeys/device-lookup', {code})
    return res.data
}

/**
 * Approves a request. {@code forAccountId} names somebody in the reader's care where a guardian is
 * signing a member in; left out, the grant is for the reader themselves.
 */
export async function deviceApprove(code: string, forAccountId?: number): Promise<void> {
    await client.post('/account/passkeys/device-approve', {code, forAccountId: forAccountId ?? null})
}

// -- Confirming a step-up on a device that is already signed in --

export interface DeviceStepUp {
    code: string
    pollSecret: string
    expiresAt: string
}

export type DeviceStepUpStatus = DevicePollStatus | 'CONFIRMED'

export async function stepUpDeviceBegin(category: string | null, operation: string | null): Promise<DeviceStepUp> {
    const res = await client.post<DeviceStepUp>('/auth/stepup/device/begin', {category, operation})
    return res.data
}

export async function stepUpDevicePoll(pollSecret: string): Promise<{status: DeviceStepUpStatus}> {
    const res = await client.post<{status: DeviceStepUpStatus}>('/auth/stepup/device/poll', {pollSecret})
    return res.data
}

// -- The token doors: a mail link, a QR in the room or a console line --

export interface TokenEnrollLookup {
    firstName: string
    lastName: string
}

export async function tokenEnrollLookup(token: string): Promise<TokenEnrollLookup> {
    const res = await client.post<TokenEnrollLookup>('/auth/passkey/token-enroll/lookup', {token})
    return res.data
}

export async function tokenEnrollBegin(token: string): Promise<PasskeyCeremony> {
    const res = await client.post<PasskeyCeremony>('/auth/passkey/token-enroll/begin', {token})
    return res.data
}

export async function tokenEnrollFinish(
    token: string,
    challengeToken: string,
    credentialJson: string,
): Promise<void> {
    await client.post('/auth/passkey/token-enroll/finish', {token, challengeToken, credentialJson})
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
