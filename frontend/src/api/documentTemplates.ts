/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {uploadFile} from './upload'
import type {
    BulkPreviewResponse,
    DocumentTemplateRequest,
    DocumentTemplateResponse,
    DocumentTemplateSummary,
    GeneratedDocumentResponse,
    GenerationJobResponse,
    GenerationJobSummary,
    JobStartRequest,
    LetterImport,
    MemberSelection,
    PlaceholderCatalogueResponse,
    PreviewResponse,
    SelfServiceOffer,
    TemplateUseRequest,
    TemplateUseResponse,
} from '@/api/generated/schema'

/**
 * The templates of one owner: a station or an association. Each lists, writes and archives its own
 * templates, says what they can name, reads a text into a letter, draws a draft and keeps the PDF of a
 * PDF template. A station's list also holds the templates of its association, marked as such.
 */
export interface TemplateSource {
    list(archived: boolean): Promise<DocumentTemplateSummary[]>
    get(id: number): Promise<DocumentTemplateResponse>
    create(request: DocumentTemplateRequest): Promise<DocumentTemplateResponse>
    update(id: number, request: DocumentTemplateRequest): Promise<DocumentTemplateResponse>
    archive(id: number): Promise<DocumentTemplateResponse>
    restore(id: number): Promise<DocumentTemplateResponse>
    /** What a template of the owner can name. */
    catalogue(): Promise<PlaceholderCatalogueResponse>
    /** Reads a Word or OpenDocument text into a template body, its gaps in brackets as placeholders. */
    importLetter(file: File): Promise<LetterImport>
    /**
     * Draws a template as the editor holds it, for a member or with its placeholders shown by name. A PDF
     * template is filled from the PDF of the saved template it changes.
     */
    previewDraft(template: DocumentTemplateRequest, templateId: number | null, memberId: number | null): Promise<PreviewResponse>
    /** Uploads a new version of the PDF a PDF template fills; its fields stay as they were. */
    uploadPdf(templateId: number, file: File): Promise<DocumentTemplateResponse>
    /** The PDF a PDF template fills now, as it was uploaded. */
    templatePdf(templateId: number): Promise<Blob>
}

/** Where an owner's template routes are. */
interface TemplatePaths {
    templates: string
    placeholders: string
    importLetter: string
    preview: string
}

function sourceAt(paths: TemplatePaths): TemplateSource {
    const at = (id: number, rest = '') => `${paths.templates}/${id}${rest}`
    return {
        async list(archived) {
            const res = await client.get<DocumentTemplateSummary[]>(paths.templates, {params: {archived}})
            return res.data
        },
        async get(id) {
            const res = await client.get<DocumentTemplateResponse>(at(id))
            return res.data
        },
        async create(request) {
            const res = await client.post<DocumentTemplateResponse>(paths.templates, request)
            return res.data
        },
        async update(id, request) {
            const res = await client.put<DocumentTemplateResponse>(at(id), request)
            return res.data
        },
        async archive(id) {
            const res = await client.post<DocumentTemplateResponse>(at(id, '/archive'))
            return res.data
        },
        async restore(id) {
            const res = await client.post<DocumentTemplateResponse>(at(id, '/restore'))
            return res.data
        },
        async catalogue() {
            const res = await client.get<PlaceholderCatalogueResponse>(paths.placeholders)
            return res.data
        },
        importLetter(file) {
            return uploadFile<LetterImport>(paths.importLetter, {file})
        },
        async previewDraft(template, templateId, memberId) {
            const res = await client.post<PreviewResponse>(paths.preview, {template, templateId, memberId})
            return res.data
        },
        uploadPdf(templateId, file) {
            return uploadFile<DocumentTemplateResponse>(at(templateId, '/pdf'), {file})
        },
        async templatePdf(templateId) {
            const res = await client.get<Blob>(at(templateId, '/pdf'), {responseType: 'blob'})
            return res.data
        },
    }
}

/** The templates of the station the reader works for, and those of its association. */
export const stationTemplateSource: TemplateSource = sourceAt({
    templates: '/document-templates',
    placeholders: '/document-placeholders',
    importLetter: '/document-template-import',
    preview: '/document-template-preview',
})

/** The templates of the association the reader acts for, which all its stations use. */
export const associationTemplateSource: TemplateSource = sourceAt({
    templates: '/cluster/document-templates',
    placeholders: '/cluster/document-placeholders',
    importLetter: '/cluster/document-template-import',
    preview: '/cluster/document-template-preview',
})

/** How the station uses a template of its association. */
export async function templateUse(templateId: number): Promise<TemplateUseResponse> {
    const res = await client.get<TemplateUseResponse>(`/document-templates/${templateId}/use`)
    return res.data
}

/** Sets whether and for whom the station offers a template of its association for self service. */
export async function setTemplateUse(templateId: number, request: TemplateUseRequest): Promise<TemplateUseResponse> {
    const res = await client.put<TemplateUseResponse>(`/document-templates/${templateId}/use`, request)
    return res.data
}

/** The templates a manager can generate documents from: the station's own and its association's. */
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

/** Draws a template for the first of many members and lists what every member lacks. */
export async function previewJob(templateId: number, selection: MemberSelection): Promise<BulkPreviewResponse> {
    const res = await client.post<BulkPreviewResponse>(`/document-generation/templates/${templateId}/jobs/preview`, selection)
    return res.data
}

/** Starts generating a template for many members in the background. */
export async function startJob(templateId: number, request: JobStartRequest): Promise<GenerationJobResponse> {
    const res = await client.post<GenerationJobResponse>(`/document-generation/templates/${templateId}/jobs`, request)
    return res.data
}

/** The latest runs of the station, the newest first. */
export async function listJobs(): Promise<GenerationJobSummary[]> {
    const res = await client.get<GenerationJobSummary[]>('/document-generation/jobs')
    return res.data
}

/** A run with how it went for each of its members. */
export async function getJob(jobId: number): Promise<GenerationJobResponse> {
    const res = await client.get<GenerationJobResponse>(`/document-generation/jobs/${jobId}`)
    return res.data
}

/** A drawn document as a file the PDF viewer can show. */
export function pdfOf(preview: PreviewResponse): Blob {
    const binary = atob(preview.pdfBase64)
    const bytes = new Uint8Array(binary.length)
    for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index)
    return new Blob([bytes], {type: 'application/pdf'})
}
