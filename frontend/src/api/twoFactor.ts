/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    BackupCodesResponse,
    LoginResultResponse,
    PasskeyStepUpBeginResponse,
    PasskeyStepUpFinishRequest,
    PasswordStepUpRequest,
    RenameFactorRequest,
    StepUpRequest,
    StepUpResponse,
    StepUpVerifiedResponse,
    TotpBeginResponse,
    TotpConfirmRequest,
    TrustedDeviceEntry,
    TrustedDevicesResponse,
    TwoFactorStatusResponse,
    Verify2faRequest,
    WebAuthnBeginResponse,
    WebAuthnLoginBeginRequest,
    WebAuthnLoginFinishRequest,
    WebAuthnRegisterFinishRequest,
    WebAuthnRegisterFinishResponse,
    WebAuthnStepUpFinishRequest,
} from './generated/schema'

export async function getTwoFactorStatus(): Promise<TwoFactorStatusResponse> {
    const res = await client.get<TwoFactorStatusResponse>('/account/2fa/status')
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
    await client.post('/account/2fa/totp/confirm', {secret, code, recoveryCodes, password} satisfies TotpConfirmRequest)
}

export async function removeTotp(): Promise<void> {
    await client.post('/account/2fa/totp/remove')
}

export async function regenerateBackupCodes(): Promise<BackupCodesResponse> {
    const res = await client.post<BackupCodesResponse>('/account/2fa/backup-codes/regenerate')
    return res.data
}

/**
 * Gives the second factor a sign-in is waiting on. The session it earned arrives as a cookie; the
 * answer says how long it lasts.
 *
 * @param trustedDevice the box from the login screen, carried through so somebody who ticked it
 *                      does not end up with the short session after the second factor.
 */
export async function verify2fa(
    preAuthToken: string,
    factor: string,
    proof: string,
    rememberDeviceDays?: number,
    trustedDevice?: boolean,
): Promise<LoginResultResponse> {
    const res = await client.post<LoginResultResponse>('/auth/2fa', {
        preAuthToken,
        factor,
        proof,
        rememberDeviceDays,
        trustedDevice,
    } satisfies Verify2faRequest)
    return res.data
}

export async function listTrustedDevices(): Promise<TrustedDeviceEntry[]> {
    const res = await client.get<TrustedDevicesResponse>('/account/2fa/trusted-devices')
    return res.data.devices
}

export async function revokeTrustedDevice(id: number): Promise<void> {
    await client.post(`/account/2fa/trusted-devices/${id}/revoke`)
}

export async function revokeAllTrustedDevices(): Promise<void> {
    await client.post('/account/2fa/trusted-devices/revoke-all')
}

export async function stepUp(factor: string, proof: string): Promise<StepUpResponse> {
    const res = await client.post<StepUpResponse>('/auth/2fa/stepup', {factor, proof} satisfies StepUpRequest)
    return res.data
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
        {challengeToken, credentialJson, label, password} satisfies WebAuthnRegisterFinishRequest,
    )
    return res.data
}

export async function webauthnLoginBegin(preAuthToken: string): Promise<WebAuthnBeginResponse> {
    const res = await client.post<WebAuthnBeginResponse>(
        '/auth/2fa/webauthn/begin',
        {preAuthToken} satisfies WebAuthnLoginBeginRequest,
    )
    return res.data
}

/**
 * @param trustedDevice the box from the login screen, carried through so a security key does not
 *                      hand back the short session to somebody who asked to stay signed in.
 */
export async function webauthnLoginFinish(
    preAuthToken: string,
    challengeToken: string,
    credentialJson: string,
    rememberDeviceDays?: number,
    trustedDevice?: boolean,
): Promise<LoginResultResponse> {
    const res = await client.post<LoginResultResponse>(
        '/auth/2fa/webauthn/finish',
        {preAuthToken, challengeToken, credentialJson, rememberDeviceDays, trustedDevice} satisfies WebAuthnLoginFinishRequest,
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
        {challengeToken, credentialJson} satisfies WebAuthnStepUpFinishRequest,
    )
    return res.data
}

/** The password, for an account that has nothing else to prove itself with. */
export async function passwordStepUp(password: string): Promise<StepUpVerifiedResponse> {
    const res = await client.post<StepUpVerifiedResponse>(
        '/auth/stepup/password',
        {password} satisfies PasswordStepUpRequest,
    )
    return res.data
}

export async function passkeyStepUpBegin(): Promise<PasskeyStepUpBeginResponse> {
    const res = await client.post<PasskeyStepUpBeginResponse>('/auth/stepup/passkey/begin')
    return res.data
}

export async function passkeyStepUpFinish(
    challengeToken: string,
    credentialJson: string,
): Promise<StepUpVerifiedResponse> {
    const res = await client.post<StepUpVerifiedResponse>(
        '/auth/stepup/passkey/finish',
        {challengeToken, credentialJson} satisfies PasskeyStepUpFinishRequest,
    )
    return res.data
}

export async function removeFactor(factorId: number): Promise<void> {
    await client.post(`/account/2fa/factors/${factorId}/remove`)
}

export async function renameFactor(factorId: number, label: string): Promise<void> {
    await client.post(`/account/2fa/factors/${factorId}/rename`, {label} satisfies RenameFactorRequest)
}
