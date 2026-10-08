/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {
    OpenSignatureResponse,
    SealVerification,
    SignerEntryDraft,
    SigningCompleteRequest,
    SigningCompleteResponse,
    SigningKeyRecoveryEntry,
    SigningKeyStatus,
    SigningStartResponse,
} from '@/api/generated/schema'
import client from './client'
import {uploadFile} from './upload'

/** The largest file the seal check takes, the same limit the server holds it to. */
export const SEAL_CHECK_MAX_BYTES = 25 * 1024 * 1024

/**
 * Checks the seals and timestamps of a PDF against this installation. Needs no session, and the
 * server keeps nothing of the file.
 *
 * @param file the PDF to check
 */
export async function verifySeals(file: File): Promise<SealVerification> {
    return uploadFile<SealVerification>('/public/signing/verify', {file})
}

/**
 * One signature field waiting for the reader, with whose document it is and in what capacity they
 * would sign it. Refused once the field no longer waits for them.
 *
 * @param fieldId the field
 */
export async function getSigningField(fieldId: number): Promise<OpenSignatureResponse> {
    const res = await client.get<OpenSignatureResponse>(`/signing/fields/${fieldId}`)
    return res.data
}

/**
 * The document a field asks the reader to sign, exactly as the request froze it, which is the file
 * the act binds to.
 *
 * @param fieldId the field
 */
export async function getSigningDocument(fieldId: number): Promise<Blob> {
    const res = await client.get<Blob>(`/signing/fields/${fieldId}/document`, {responseType: 'blob'})
    return res.data
}

/**
 * Starts a signing act on a field. The start is kept on the server for a few minutes and spent by
 * the first confirmation, whether it passes or not.
 *
 * @param fieldId the field
 * @param entries what the signer typed into fields of their own, none where the document asks for none
 */
export async function startSigning(fieldId: number, entries: SignerEntryDraft[] = []): Promise<SigningStartResponse> {
    const res = await client.post<SigningStartResponse>(`/signing/fields/${fieldId}/start`, {entries})
    return res.data
}

/**
 * Confirms a started signing act with one proof.
 *
 * @param fieldId      the field the act was started for
 * @param confirmation the start's token, the proof, and the authenticator's answer or the code or password
 */
export async function completeSigning(
    fieldId: number,
    confirmation: SigningCompleteRequest,
): Promise<SigningCompleteResponse> {
    const res = await client.post<SigningCompleteResponse>(`/signing/fields/${fieldId}/complete`, confirmation)
    return res.data
}

/** Which of the installation's signing keys no longer open under the at-rest secret, for its administrators. */
export async function getSigningKeyStatus(): Promise<SigningKeyStatus> {
    const res = await client.get<SigningKeyStatus>('/admin/signing/keys')
    return res.data
}

/**
 * Gives up the signing keys that no longer open, so the next seal of each station issues new ones.
 * Asks for a fresh second factor, and runs only while the keys that do not open are exactly these.
 *
 * @param serialNumbers the serial numbers of the keys shown as no longer opening
 */
export async function recoverSigningKeys(serialNumbers: string[]): Promise<SigningKeyRecoveryEntry> {
    const res = await client.post<SigningKeyRecoveryEntry>('/admin/signing/keys/recover', {serialNumbers})
    return res.data
}
