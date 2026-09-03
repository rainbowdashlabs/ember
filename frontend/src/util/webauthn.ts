/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Browser-side WebAuthn helpers that bridge the Yubico server JSON shape (URL-safe base64
 * fields) to the {@code BufferSource} fields that {@code navigator.credentials} expects, and
 * back again for the response.
 */

export function isWebAuthnSupported(): boolean {
    return typeof window !== 'undefined'
        && !!window.PublicKeyCredential
        && typeof window.PublicKeyCredential === 'function'
}

function base64UrlToBuffer(value: string): ArrayBuffer {
    const padded = value.replace(/-/g, '+').replace(/_/g, '/')
    const padding = '='.repeat((4 - padded.length % 4) % 4)
    const binary = atob(padded + padding)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    return bytes.buffer
}

function bufferToBase64Url(buffer: ArrayBuffer | null | undefined): string {
    if (!buffer) return ''
    const bytes = new Uint8Array(buffer)
    let binary = ''
    for (const byte of bytes) binary += String.fromCharCode(byte)
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** A credential descriptor as the server serialises it: the id arrives URL-safe base64 encoded. */
interface ServerCredentialDescriptor extends Omit<PublicKeyCredentialDescriptor, 'id'> {
    id: string
}

/** Server JSON for a registration ceremony: the binary fields arrive URL-safe base64 encoded. */
interface ServerCreationOptions extends Omit<PublicKeyCredentialCreationOptions, 'challenge' | 'user' | 'excludeCredentials'> {
    challenge: string
    user: Omit<PublicKeyCredentialUserEntity, 'id'> & {id: string}
    excludeCredentials?: ServerCredentialDescriptor[]
}

/** Server JSON for an assertion ceremony: the binary fields arrive URL-safe base64 encoded. */
interface ServerRequestOptions extends Omit<PublicKeyCredentialRequestOptions, 'challenge' | 'allowCredentials'> {
    challenge: string
    allowCredentials?: ServerCredentialDescriptor[]
}

function unwrapPublicKey<T>(json: string): T {
    const raw = JSON.parse(json) as T & {publicKey?: T}
    return raw.publicKey ?? raw
}

function decodeDescriptors(descriptors: ServerCredentialDescriptor[] | undefined): PublicKeyCredentialDescriptor[] {
    return (descriptors ?? []).map((descriptor) => ({
        ...descriptor,
        id: base64UrlToBuffer(descriptor.id),
    }))
}

/**
 * The server hands us the JSON form of {@code PublicKeyCredentialCreationOptions} produced
 * by {@code com.yubico.webauthn.data.PublicKeyCredentialCreationOptions#toCredentialsCreateJson()}.
 * Either Yubico variant works - this helper massages the inputs for the WebAuthn DOM API.
 */
function decodeCreationOptions(json: string): PublicKeyCredentialCreationOptions {
    const source = unwrapPublicKey<ServerCreationOptions>(json)
    return {
        ...source,
        challenge: base64UrlToBuffer(source.challenge),
        user: {
            ...source.user,
            id: base64UrlToBuffer(source.user.id),
        },
        excludeCredentials: decodeDescriptors(source.excludeCredentials),
    }
}

function decodeRequestOptions(json: string): PublicKeyCredentialRequestOptions {
    const source = unwrapPublicKey<ServerRequestOptions>(json)
    return {
        ...source,
        challenge: base64UrlToBuffer(source.challenge),
        allowCredentials: decodeDescriptors(source.allowCredentials),
    }
}

/**
 * Encodes the {@code PublicKeyCredential} returned by {@code navigator.credentials.create}
 * back to the URL-safe base64 JSON that Yubico's
 * {@code PublicKeyCredential#parseRegistrationResponseJson} expects.
 */
function encodeRegistrationResponse(credential: PublicKeyCredential): string {
    const response = credential.response as AuthenticatorAttestationResponse
    const transports = typeof response.getTransports === 'function' ? response.getTransports() : []
    return JSON.stringify({
        type: credential.type,
        id: credential.id,
        rawId: bufferToBase64Url(credential.rawId),
        authenticatorAttachment: credential.authenticatorAttachment ?? null,
        response: {
            attestationObject: bufferToBase64Url(response.attestationObject),
            clientDataJSON: bufferToBase64Url(response.clientDataJSON),
            transports,
        },
        clientExtensionResults: credential.getClientExtensionResults(),
    })
}

function encodeAssertionResponse(credential: PublicKeyCredential): string {
    const response = credential.response as AuthenticatorAssertionResponse
    return JSON.stringify({
        type: credential.type,
        id: credential.id,
        rawId: bufferToBase64Url(credential.rawId),
        authenticatorAttachment: credential.authenticatorAttachment ?? null,
        response: {
            authenticatorData: bufferToBase64Url(response.authenticatorData),
            clientDataJSON: bufferToBase64Url(response.clientDataJSON),
            signature: bufferToBase64Url(response.signature),
            userHandle: response.userHandle ? bufferToBase64Url(response.userHandle) : null,
        },
        clientExtensionResults: credential.getClientExtensionResults(),
    })
}

export async function createWebAuthnCredential(optionsJson: string): Promise<string> {
    if (!isWebAuthnSupported()) throw new Error('webauthn-unsupported')
    const options = decodeCreationOptions(optionsJson)
    const credential = await navigator.credentials.create({publicKey: options})
    if (!credential) throw new Error('webauthn-cancelled')
    return encodeRegistrationResponse(credential as PublicKeyCredential)
}

export async function getWebAuthnCredential(optionsJson: string): Promise<string> {
    if (!isWebAuthnSupported()) throw new Error('webauthn-unsupported')
    const options = decodeRequestOptions(optionsJson)
    const credential = await navigator.credentials.get({publicKey: options})
    if (!credential) throw new Error('webauthn-cancelled')
    return encodeAssertionResponse(credential as PublicKeyCredential)
}

/**
 * The autofill path: the browser offers whatever passkeys it holds the moment the reader clicks
 * the username field, which is what makes a passkey feel like nothing at all. Resolves when the
 * reader picked one; the caller aborts through the signal when the form is submitted the
 * ordinary way instead.
 */
export async function getWebAuthnCredentialConditional(optionsJson: string, signal: AbortSignal): Promise<string> {
    if (!isWebAuthnSupported()) throw new Error('webauthn-unsupported')
    const options = decodeRequestOptions(optionsJson)
    const credential = await navigator.credentials.get({publicKey: options, mediation: 'conditional', signal})
    if (!credential) throw new Error('webauthn-cancelled')
    return encodeAssertionResponse(credential as PublicKeyCredential)
}

/** Whether the browser can offer passkeys through the username field's autofill. */
export async function isConditionalMediationAvailable(): Promise<boolean> {
    if (!isWebAuthnSupported()) return false
    try {
        return await (window.PublicKeyCredential.isConditionalMediationAvailable?.() ?? Promise.resolve(false))
    } catch {
        return false
    }
}

/** Whether this device can hold a passkey of its own (fingerprint, face or device PIN). */
export async function isPlatformAuthenticatorAvailable(): Promise<boolean> {
    if (!isWebAuthnSupported()) return false
    try {
        return await window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()
    } catch {
        return false
    }
}

/**
 * Every way a ceremony can fail, mapped to the sentence the member sees. The rows point at a way
 * forward rather than at a fault; the caller passes whether it was a creation, because
 * InvalidStateError means something specific there.
 */
export function webauthnErrorKey(e: unknown, context: 'create' | 'get'): string {
    const name = (e as {name?: string; message?: string} | undefined)?.name
    const message = (e as Error | undefined)?.message
    if (message === 'webauthn-unsupported') return 'passkeys.errors.notSupported'
    if (message === 'webauthn-cancelled') return 'passkeys.errors.notAllowed'
    switch (name) {
        case 'NotAllowedError':
            return 'passkeys.errors.notAllowed'
        case 'InvalidStateError':
            return context === 'create' ? 'passkeys.errors.alreadyRegistered' : 'passkeys.errors.generic'
        case 'NotSupportedError':
            return 'passkeys.errors.notSupported'
        case 'SecurityError':
            return 'passkeys.errors.security'
        case 'AbortError':
            return 'passkeys.errors.aborted'
        default:
            return 'passkeys.errors.generic'
    }
}

interface SignalCapableCredential {
    signalAllAcceptedCredentials?: (options: {rpId: string; userId: string; allAcceptedCredentialIds: string[]}) => Promise<void>
    signalCurrentUserDetails?: (options: {rpId: string; userId: string; name: string; displayName: string}) => Promise<void>
}

/**
 * Tells the authenticator which credentials still exist, so a passkey deleted here disappears
 * from the member's phone instead of haunting its account picker. Best effort: the signal
 * methods are new and their absence must never break the screen that calls this.
 */
export async function signalAcceptedCredentials(rpId: string, userId: string, credentialIds: string[]): Promise<void> {
    if (!isWebAuthnSupported()) return
    const signal = (window.PublicKeyCredential as unknown as SignalCapableCredential).signalAllAcceptedCredentials
    if (!signal) return
    try {
        await signal({rpId, userId, allAcceptedCredentialIds: credentialIds})
    } catch {
        // The device keeps a stale entry; the next successful signal clears it.
    }
}

/** Keeps the name the device shows beside a passkey in step with the account. Best effort. */
export async function signalUserDetails(rpId: string, userId: string, name: string, displayName: string): Promise<void> {
    if (!isWebAuthnSupported()) return
    const signal = (window.PublicKeyCredential as unknown as SignalCapableCredential).signalCurrentUserDetails
    if (!signal) return
    try {
        await signal({rpId, userId, name, displayName})
    } catch {
        // Nothing to do: the stale name costs nothing.
    }
}
