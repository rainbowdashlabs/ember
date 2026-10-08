/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {
    FillInResponse,
    ManagedRequestResponse,
    OpenSignatureResponse,
    SealVerification,
    SignatureAskResponse,
    SignatureImageSource,
    SignatureSettingsResponse,
    SignerEntryDraft,
    SigningCompleteRequest,
    SigningCompleteResponse,
    SigningKeyRecoveryEntry,
    SigningKeyStatus,
    SigningStartResponse,
} from '@/api/generated/schema'
import client from './client'
import {uploadFile} from './upload'

/** The largest signature picture the server takes, the same limit it holds an upload to. */
export const SIGNATURE_IMAGE_MAX_BYTES = 5 * 1024 * 1024

/** The reader's own signature picture, whether one is saved, and their consent to letters signed with it. */
export async function getSignatureSettings(): Promise<SignatureSettingsResponse> {
    const res = await client.get<SignatureSettingsResponse>('/session/signature')
    return res.data
}

/** The reader's own signature picture, or null where none is saved. */
export async function getSignatureImage(): Promise<Blob | null> {
    const res = await client.get<Blob>('/session/signature/image', {responseType: 'blob'})
    return res.status === 204 ? null : res.data
}

/**
 * Saves a signature picture as the reader's own, in place of the one before. The server cuts it to the
 * signature and makes its background transparent.
 *
 * @param image  the picture: drawn, typed, or a photo or scan
 * @param source how it was made
 */
export async function saveSignatureImage(image: Blob, source: SignatureImageSource): Promise<SignatureSettingsResponse> {
    return uploadFile<SignatureSettingsResponse>('/session/signature/image', {image, source}, 'put')
}

/** Deletes the reader's own signature picture. The consent stays as it was. */
export async function deleteSignatureImage(): Promise<SignatureSettingsResponse> {
    const res = await client.delete<SignatureSettingsResponse>('/session/signature/image')
    return res.data
}

/**
 * Agrees to, or takes back, letters the reader issues being signed with their picture automatically.
 *
 * @param consented whether they agree from now on
 */
export async function setSignatureConsent(consented: boolean): Promise<SignatureSettingsResponse> {
    const res = await client.put<SignatureSettingsResponse>('/session/signature/consent', {consented})
    return res.data
}

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
 * The fields the document asks the reader to fill in when they sign a field, as the frozen document
 * carries them; none where it asks for none.
 *
 * @param fieldId the signature field
 */
export async function getSigningFillIns(fieldId: number): Promise<FillInResponse[]> {
    const res = await client.get<FillInResponse[]>(`/signing/fields/${fieldId}/fill-ins`)
    return res.data
}

/**
 * Who a generated document would ask to sign each of its fields and what they confirm, or the request
 * already made for it with how its fields stand. Looking asks nobody.
 *
 * @param generationId the document's entry in the generation log
 */
export async function getSignatureAsk(generationId: number): Promise<SignatureAskResponse> {
    const res = await client.get<SignatureAskResponse>(`/signing/generations/${generationId}`)
    return res.data
}

/**
 * Asks for the signatures a generated document's fields call for. Everybody asked is told, in the app
 * and by mail.
 *
 * @param generationId the document's entry in the generation log
 */
export async function requestSignatures(generationId: number): Promise<SignatureAskResponse> {
    const res = await client.post<SignatureAskResponse>(`/signing/generations/${generationId}/request`)
    return res.data
}

/**
 * A request for signatures as its document's manager sees it: every field with its signer, statement,
 * state and the act that signed it, and the documents it could be asked anew on.
 *
 * @param requestUid the request
 */
export async function getSignatureRequest(requestUid: string): Promise<ManagedRequestResponse> {
    const res = await client.get<ManagedRequestResponse>(`/signing/requests/${requestUid}`)
    return res.data
}

/** What a manager can do to one field that still waits for a signature. */
export const FieldSettlement = {
    PAPER: 'paper',
    WAIVE: 'waive',
    WITHDRAW: 'withdraw',
} as const

export type FieldSettlement = typeof FieldSettlement[keyof typeof FieldSettlement]

/**
 * Settles a field no signing act will: confirmed on paper, waived or withdrawn. The next sealed version of
 * the document shows it so.
 *
 * @param requestUid the request
 * @param fieldName  the field
 * @param settlement what becomes of it
 */
export async function settleSignatureField(
    requestUid: string,
    fieldName: string,
    settlement: FieldSettlement,
): Promise<ManagedRequestResponse> {
    const res = await client.post<ManagedRequestResponse>(
        `/signing/requests/${requestUid}/fields/${encodeURIComponent(fieldName)}/${settlement}`)
    return res.data
}

/**
 * Stops asking for every signature a request still waits for; what was signed stays.
 *
 * @param requestUid the request
 */
export async function withdrawSignatureRequest(requestUid: string): Promise<ManagedRequestResponse> {
    const res = await client.post<ManagedRequestResponse>(`/signing/requests/${requestUid}/withdraw`)
    return res.data
}

/**
 * Asks a request's signatures anew on a document generated again for the same member after a change. The
 * old request is replaced; what was signed on it stays.
 *
 * @param requestUid   the request to replace
 * @param generationId the corrected document's entry in the generation log
 * @return the new request
 */
export async function rectifySignatureRequest(requestUid: string, generationId: number): Promise<ManagedRequestResponse> {
    const res = await client.post<ManagedRequestResponse>(`/signing/requests/${requestUid}/rectify`, {generationId})
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
