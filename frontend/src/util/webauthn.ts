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
 * Either Yubico variant works — this helper massages the inputs for the WebAuthn DOM API.
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
