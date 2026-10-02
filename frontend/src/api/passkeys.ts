/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {isStorageDenied} from './storage'
import {StorageDeniedError} from './auth'
import type {PasskeyModeName} from './adminSettings'
import type {
    CeremonyResponse,
    CreationFinishRequest,
    DeviceCodeRequest,
    DeviceEnrollBeginRequest,
    DeviceEnrollFinishRequest,
    DeviceIdentifierRequest,
    DeviceLookupResponse,
    DevicePollRequest,
    DevicePollResponse,
    DeviceRequestResponse,
    DeviceStepUpBeginRequest,
    DeviceStepUpBeginResponse,
    DeviceStepUpPollRequest,
    DeviceStepUpPollResponse,
    LoginResponse,
    OfferAnswer,
    OfferAnswerRequest,
    OfferResponse,
    PasskeyEntryResponse,
    PasskeysStatusResponse,
    PublicModeResponse,
    RemovalResponse,
    RenameRequest,
    SignInClaimRequest,
    SignInFinishRequest,
    SwitchRequest,
    TokenEnrollFinishRequest,
    TokenEnrollLookupResponse,
    TokenEnrollRequest,
    TrialOutcome,
    TrialResponse,
} from './generated/schema'

export async function publicPasskeyMode(): Promise<PasskeyModeName> {
    const res = await client.get<PublicModeResponse>('/public/settings/passkeys')
    return res.data.mode
}

export async function passkeySignInBegin(): Promise<CeremonyResponse> {
    const res = await client.post<CeremonyResponse>('/auth/passkey/begin')
    return res.data
}

/** Finishes the sign-in; the session arrives as a cookie, the way a password login's does. */
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
    } satisfies SignInFinishRequest)
    return res.data
}

export async function getPasskeysStatus(): Promise<PasskeysStatusResponse> {
    const res = await client.get<PasskeysStatusResponse>('/account/passkeys')
    return res.data
}

export async function passkeyCreateBegin(): Promise<CeremonyResponse> {
    const res = await client.post<CeremonyResponse>('/account/passkeys/begin')
    return res.data
}

export async function passkeyCreateFinish(
    challengeToken: string,
    credentialJson: string,
    label: string,
): Promise<PasskeyEntryResponse> {
    const res = await client.post<PasskeyEntryResponse>(
        '/account/passkeys/finish',
        {challengeToken, credentialJson, label} satisfies CreationFinishRequest,
    )
    return res.data
}

export async function renamePasskey(id: number, label: string): Promise<void> {
    await client.post(`/account/passkeys/${id}/rename`, {label} satisfies RenameRequest)
}

export async function removePasskey(id: number): Promise<RemovalResponse> {
    const res = await client.delete<RemovalResponse>(`/account/passkeys/${id}`)
    return res.data
}

export async function setPasswordLogin(enabled: boolean): Promise<void> {
    await client.post('/account/passkeys/password-login', {enabled} satisfies SwitchRequest)
}

export async function setAskWithPassword(enabled: boolean): Promise<void> {
    await client.post('/account/passkeys/second-factor', {enabled} satisfies SwitchRequest)
}

export async function offerState(): Promise<boolean> {
    const res = await client.get<OfferResponse>('/account/passkeys/offer')
    return res.data.offer
}

/** How a member answers the offer to set up a passkey: ask again later, or never again. */
export async function answerOffer(answer: OfferAnswer): Promise<void> {
    await client.post('/account/passkeys/offer-answer', {answer} satisfies OfferAnswerRequest)
}

export async function deviceRequest(identifier: string): Promise<DeviceRequestResponse> {
    const res = await client.post<DeviceRequestResponse>(
        '/auth/passkey/device-request',
        {identifier} satisfies DeviceIdentifierRequest,
    )
    return res.data
}

/**
 * Asks to be signed in rather than given a credential. Nothing is left on this device, and the
 * instance does not need passkeys switched on at all.
 *
 * <p>The account is named here rather than at the approval, so a code raised for one person cannot
 * be answered by another. An address that belongs to nobody is answered exactly like one that does,
 * so the screen says nothing about who has an account here.
 */
export async function signInRequest(identifier: string): Promise<DeviceRequestResponse> {
    const res = await client.post<DeviceRequestResponse>(
        '/auth/device/sign-in-request',
        {identifier} satisfies DeviceIdentifierRequest,
    )
    return res.data
}

/**
 * Spends the claim; the session it bought arrives as a cookie, the way a password or passkey
 * sign-in's does.
 */
export async function signInClaim(claimToken: string): Promise<LoginResponse> {
    if (isStorageDenied()) {
        throw new StorageDeniedError()
    }
    const res = await client.post<LoginResponse>('/auth/device/sign-in-claim', {claimToken} satisfies SignInClaimRequest)
    return res.data
}

export async function devicePoll(pollSecret: string): Promise<DevicePollResponse> {
    const res = await client.post<DevicePollResponse>(
        '/auth/passkey/device-request/poll',
        {pollSecret} satisfies DevicePollRequest,
    )
    return res.data
}

export async function deviceEnrollBegin(enrollToken: string): Promise<CeremonyResponse> {
    const res = await client.post<CeremonyResponse>(
        '/auth/passkey/enroll/begin',
        {enrollToken} satisfies DeviceEnrollBeginRequest,
    )
    return res.data
}

export async function deviceEnrollFinish(
    enrollToken: string,
    challengeToken: string,
    credentialJson: string,
): Promise<void> {
    await client.post(
        '/auth/passkey/enroll/finish',
        {enrollToken, challengeToken, credentialJson} satisfies DeviceEnrollFinishRequest,
    )
}

export async function deviceLookup(code: string): Promise<DeviceLookupResponse> {
    const res = await client.post<DeviceLookupResponse>(
        '/account/passkeys/device-lookup',
        {code} satisfies DeviceCodeRequest,
    )
    return res.data
}

/**
 * Approves a request. {@code forAccountId} names somebody in the reader's care where a guardian is
 * signing a member in; left out, the grant is for the reader themselves.
 *
 * <p>{@code pickedNumber} is the choice the reader made. The wrong one ends the request: the server
 * answers 409 and the code is spent, so there is no second guess on it.
 */
export async function deviceApprove(code: string, pickedNumber: number, forAccountId?: number): Promise<void> {
    await client.post(
        '/account/passkeys/device-approve',
        {code, pickedNumber, forAccountId} satisfies DeviceCodeRequest,
    )
}

/**
 * Raises the request the other device will confirm. The category is all that travels: it is what the
 * approval screen shows and the only thing a confirmation answers.
 */
export async function stepUpDeviceBegin(category: string): Promise<DeviceStepUpBeginResponse> {
    const res = await client.post<DeviceStepUpBeginResponse>(
        '/auth/stepup/device/begin',
        {category} satisfies DeviceStepUpBeginRequest,
    )
    return res.data
}

export async function stepUpDevicePoll(pollSecret: string): Promise<DeviceStepUpPollResponse> {
    const res = await client.post<DeviceStepUpPollResponse>(
        '/auth/stepup/device/poll',
        {pollSecret} satisfies DeviceStepUpPollRequest,
    )
    return res.data
}

export async function tokenEnrollLookup(token: string): Promise<TokenEnrollLookupResponse> {
    const res = await client.post<TokenEnrollLookupResponse>(
        '/auth/passkey/token-enroll/lookup',
        {token} satisfies TokenEnrollRequest,
    )
    return res.data
}

export async function tokenEnrollBegin(token: string): Promise<CeremonyResponse> {
    const res = await client.post<CeremonyResponse>(
        '/auth/passkey/token-enroll/begin',
        {token} satisfies TokenEnrollRequest,
    )
    return res.data
}

export async function tokenEnrollFinish(
    token: string,
    challengeToken: string,
    credentialJson: string,
): Promise<void> {
    await client.post(
        '/auth/passkey/token-enroll/finish',
        {token, challengeToken, credentialJson} satisfies TokenEnrollFinishRequest,
    )
}

export async function trialBegin(): Promise<CeremonyResponse> {
    const res = await client.post<CeremonyResponse>('/account/passkeys/trial/begin')
    return res.data
}

export async function trialFinish(challengeToken: string, credentialJson: string): Promise<TrialOutcome> {
    const res = await client.post<TrialResponse>(
        '/account/passkeys/trial/finish',
        {challengeToken, credentialJson} satisfies SignInFinishRequest,
    )
    return res.data.outcome
}
