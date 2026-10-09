/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {uploadFile} from './upload'
import type {TemplatePages} from './documentTemplates'
import type {
    AgreementSigner,
    AppointmentDocuments,
    GeneratedDocumentResponse,
    PaperSubmission,
    RequiredTemplate,
    RequirementSignature,
    TemplatePage,
} from '@/api/generated/schema'

/** Who asks for documents to bring: an appointment or an appointment template, by id. */
export interface RequirementOwner {
    kind: 'events' | 'event-templates'
    id: number
}

/** One page of the document templates appointments may ask participants to bring. */
export const offeredTemplates: TemplatePages = async query => {
    const res = await client.get<TemplatePage>('/document-requirements/templates', {params: query})
    return res.data
}

/** The documents an appointment or an appointment template asks for, in their order. */
export async function listRequirements(owner: RequirementOwner): Promise<RequiredTemplate[]> {
    const res = await client.get<RequiredTemplate[]>(`/${owner.kind}/${owner.id}/document-requirements`)
    return res.data
}

/** Sets the documents an appointment or an appointment template asks for. */
export async function setRequirements(owner: RequirementOwner, templateIds: number[]): Promise<RequiredTemplate[]> {
    const res = await client.put<RequiredTemplate[]>(`/${owner.kind}/${owner.id}/document-requirements`, {templateIds})
    return res.data
}

/**
 * The documents an appointment asks for on a date: the list for every reader, the copies of the reader
 * and the members in their care who take part, and every participant's for whoever manages the
 * registrations.
 */
export async function documentsToBring(eventId: number, date: string): Promise<AppointmentDocuments> {
    const res = await client.get<AppointmentDocuments>(`/events/${eventId}/documents-to-bring`, {params: {date}})
    return res.data
}

/** Where the first page of a document the appointment asks for is served, for whoever sees the appointment. */
export function toBringPictureUrl(eventId: number, templateId: number, size: number): string {
    return `/events/${eventId}/documents-to-bring/${templateId}/picture?size=${size}`
}

/** Generates a participant's copy of a document the appointment asks for and files it with them. */
export async function generateToBring(
    eventId: number,
    date: string,
    templateId: number,
    memberId: number,
): Promise<GeneratedDocumentResponse> {
    const res = await client.post<GeneratedDocumentResponse>(
        `/events/${eventId}/documents-to-bring/${templateId}/members/${memberId}`, null, {params: {date}})
    return res.data
}

/**
 * Asks for the signatures of an appointment's agreement for a participant, on an appointment that takes no
 * registrations: the copy is filed and its fields wait for the reader, who signs them next.
 */
export async function offerAgreement(
    eventId: number,
    date: string,
    templateId: number,
    memberId: number,
): Promise<RequirementSignature> {
    const res = await client.post<RequirementSignature>(
        `/events/${eventId}/documents-to-bring/${templateId}/members/${memberId}/agreement`, null, {params: {date}})
    return res.data
}

/** Who signed the documents an appointment asks for on a date, for whoever runs it. */
export async function agreementSigners(eventId: number, date: string): Promise<AgreementSigner[]> {
    const res = await client.get<AgreementSigner[]>(`/events/${eventId}/agreement-signers`, {params: {date}})
    return res.data
}

/** What a scan of a signed paper copy is handed in for: one participant's document on one date. */
export interface ScanTarget {
    eventId: number
    date: string
    templateId: number
    memberId: number
}

/**
 * Hands in the scan of a participant's signed paper copy and files it in their documents. One handed in
 * by a manager of the registrations counts as confirmed at once.
 */
export async function submitScan(target: ScanTarget, file: File, title: string): Promise<PaperSubmission> {
    const date = encodeURIComponent(target.date)
    return uploadFile<PaperSubmission>(
        `/events/${target.eventId}/documents-to-bring/${target.templateId}/members/${target.memberId}/scan?date=${date}`,
        {file, title})
}

/** Takes back a scan that still waits; it is removed and the document is open again. */
export async function withdrawScan(eventId: number, submissionId: number): Promise<void> {
    await client.delete(`/events/${eventId}/document-scans/${submissionId}`)
}

/**
 * Where a participant's copy of a document the appointment asks for is served to whoever manages the
 * registrations: its sealed version once it was signed online, otherwise the copy as filed.
 */
export function participantCopyUrl(target: ScanTarget): string {
    const date = encodeURIComponent(target.date)
    return `/events/${target.eventId}/documents-to-bring/${target.templateId}/members/${target.memberId}/copy?date=${date}`
}

/** Where a scan handed in is served, for whoever manages the registrations. */
export function scanContentUrl(eventId: number, submissionId: number): string {
    return `/events/${eventId}/document-scans/${submissionId}/content`
}

/** Confirms a scan handed in as the participant's signed paper copy. */
export async function confirmScan(eventId: number, submissionId: number): Promise<PaperSubmission> {
    const res = await client.post<PaperSubmission>(`/events/${eventId}/document-scans/${submissionId}/confirm`)
    return res.data
}

/** Turns a scan handed in down; the participant and their guardians are told the reason. */
export async function rejectScan(eventId: number, submissionId: number, reason: string): Promise<PaperSubmission> {
    const res = await client.post<PaperSubmission>(`/events/${eventId}/document-scans/${submissionId}/reject`, {reason})
    return res.data
}
