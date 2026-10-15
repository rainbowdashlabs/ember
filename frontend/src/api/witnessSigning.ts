/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {
    BatchCompleteRequest,
    BatchCompleteResponse,
    BatchStartResponse,
    FillInResponse,
    SignerEntryDraft,
    WitnessFieldResponse,
    WitnessOffersResponse,
    WitnessPersonResponse,
} from '@/api/generated/schema'
import client from './client'

/** The people the reader may collect signatures from in person, by name. */
export async function getWitnessPeople(): Promise<WitnessPersonResponse[]> {
    const res = await client.get<WitnessPersonResponse[]>('/signing/witness/people')
    return res.data
}

/**
 * What the reader may collect in person from one person: the open fields of their documents and the
 * templates a document can be made from on the spot.
 *
 * @param memberId the person
 */
export async function getWitnessOffers(memberId: number): Promise<WitnessOffersResponse> {
    const res = await client.get<WitnessOffersResponse>(`/signing/witness/people/${memberId}/offers`)
    return res.data
}

/**
 * Makes a document from a template for the person and asks for its signatures without telling anybody,
 * so they can be signed in front of the reader right away.
 *
 * @param memberId   the person
 * @param templateId the template
 * @return the fields of the new document the reader may collect
 */
export async function generateForWitness(memberId: number, templateId: number): Promise<WitnessFieldResponse[]> {
    const res = await client.post<WitnessFieldResponse[]>(`/signing/witness/people/${memberId}/templates/${templateId}`)
    return res.data
}

/**
 * One open field the reader may collect in person, with the people it names.
 *
 * @param fieldId the field
 */
export async function getWitnessField(fieldId: number): Promise<WitnessFieldResponse> {
    const res = await client.get<WitnessFieldResponse>(`/signing/witness/fields/${fieldId}`)
    return res.data
}

/**
 * The document of a field the reader may collect, exactly as it was frozen.
 *
 * @param fieldId the field
 */
export async function getWitnessDocument(fieldId: number): Promise<Blob> {
    const res = await client.get<Blob>(`/signing/witness/fields/${fieldId}/document`, {responseType: 'blob'})
    return res.data
}

/**
 * The fields the person fills in when they sign a field in front of the reader.
 *
 * @param fieldId the field
 */
export async function getWitnessFillIns(fieldId: number): Promise<FillInResponse[]> {
    const res = await client.get<FillInResponse[]>(`/signing/witness/fields/${fieldId}/fill-ins`)
    return res.data
}

/**
 * Starts the act of a person signing a field in front of the reader. The start is kept on the server for a
 * few minutes and spent by the first confirmation.
 *
 * @param fieldId  the field
 * @param signerId the person in front of the reader
 * @param entries  what the person typed into the fields they fill in
 */
export async function startWitnessedSigning(
    fieldId: number,
    signerId: number,
    entries: SignerEntryDraft[],
): Promise<BatchStartResponse> {
    const res = await client.post<BatchStartResponse>(`/signing/witness/fields/${fieldId}/start`, {signerId, entries})
    return res.data
}

/**
 * Confirms, as the witness, that the person signed in front of the reader.
 *
 * @param confirmation the start's token, the reader's proof and the picture the person drew
 */
export async function completeWitnessedSigning(confirmation: BatchCompleteRequest): Promise<BatchCompleteResponse> {
    const res = await client.post<BatchCompleteResponse>('/signing/witness/complete', confirmation)
    return res.data
}
