/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {uploadFile} from './upload'
import type {
    DocumentTemplateRequest,
    DocumentTemplateResponse,
    DocumentTemplateSummary,
    GeneratedDocumentResponse,
    LetterImport,
    PlaceholderCatalogueResponse,
    PreviewResponse,
    SelfServiceOffer,
} from '@/api/generated/schema'

/** The templates of the station, those in use or the archived ones. */
export async function listTemplates(archived = false): Promise<DocumentTemplateSummary[]> {
    const res = await client.get<DocumentTemplateSummary[]>('/document-templates', {params: {archived}})
    return res.data
}

export async function getTemplate(id: number): Promise<DocumentTemplateResponse> {
    const res = await client.get<DocumentTemplateResponse>(`/document-templates/${id}`)
    return res.data
}

export async function createTemplate(request: DocumentTemplateRequest): Promise<DocumentTemplateResponse> {
    const res = await client.post<DocumentTemplateResponse>('/document-templates', request)
    return res.data
}

export async function updateTemplate(id: number, request: DocumentTemplateRequest): Promise<DocumentTemplateResponse> {
    const res = await client.put<DocumentTemplateResponse>(`/document-templates/${id}`, request)
    return res.data
}

export async function archiveTemplate(id: number): Promise<DocumentTemplateResponse> {
    const res = await client.post<DocumentTemplateResponse>(`/document-templates/${id}/archive`)
    return res.data
}

export async function restoreTemplate(id: number): Promise<DocumentTemplateResponse> {
    const res = await client.post<DocumentTemplateResponse>(`/document-templates/${id}/restore`)
    return res.data
}

/** What a template of the station can name, and the choice questions pronouns can follow. */
export async function getCatalogue(): Promise<PlaceholderCatalogueResponse> {
    const res = await client.get<PlaceholderCatalogueResponse>('/document-placeholders')
    return res.data
}

/** Reads a Word or OpenDocument text into a template body, its gaps in brackets as placeholders. */
export async function importLetter(file: File): Promise<LetterImport> {
    return uploadFile<LetterImport>('/document-template-import', {file})
}

/** Draws a template as the editor holds it, for a member or with its placeholders shown by name. */
export async function previewDraft(template: DocumentTemplateRequest, memberId: number | null): Promise<PreviewResponse> {
    const res = await client.post<PreviewResponse>('/document-template-preview', {template, memberId})
    return res.data
}

/** The templates a manager can generate documents from. */
export async function usableTemplates(): Promise<DocumentTemplateSummary[]> {
    const res = await client.get<DocumentTemplateSummary[]>('/document-generation/templates')
    return res.data
}

export async function previewForMember(templateId: number, memberId: number): Promise<PreviewResponse> {
    const res = await client.post<PreviewResponse>(`/document-generation/templates/${templateId}/members/${memberId}/preview`)
    return res.data
}

export async function generateForMember(templateId: number, memberId: number): Promise<GeneratedDocumentResponse> {
    const res = await client.post<GeneratedDocumentResponse>(`/document-generation/templates/${templateId}/members/${memberId}`)
    return res.data
}

/** The templates the reader may generate for themselves or a member in their care. */
export async function selfServiceOffers(memberId: number): Promise<SelfServiceOffer[]> {
    const res = await client.get<SelfServiceOffer[]>(`/self-service/documents/${memberId}`)
    return res.data
}

export async function generateForSelf(memberId: number, templateId: number): Promise<GeneratedDocumentResponse> {
    const res = await client.post<GeneratedDocumentResponse>(`/self-service/documents/${memberId}/templates/${templateId}`)
    return res.data
}

/** A drawn document as a file the PDF viewer can show. */
export function pdfOf(preview: PreviewResponse): Blob {
    const binary = atob(preview.pdfBase64)
    const bytes = new Uint8Array(binary.length)
    for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index)
    return new Blob([bytes], {type: 'application/pdf'})
}
