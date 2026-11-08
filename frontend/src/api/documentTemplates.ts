/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import {uploadFile} from './upload'
import type {
    BulkPreviewResponse,
    DateFormatCheck,
    DateFormatCheckRequest,
    DocumentTemplateCopy,
    DocumentTemplateCopyRequest,
    DocumentTemplateRequest,
    DocumentTemplateResponse,
    DocumentTemplateSummary,
    GeneratedDocumentEntry,
    GeneratedDocumentResponse,
    GenerationJobResponse,
    GenerationJobSummary,
    IssuerChoice,
    JobPreviewRequest,
    JobStartRequest,
    LetterImport,
    ManagerGenerationRequest,
    MissingFonts,
    PlaceholderCatalogueResponse,
    PreviewResponse,
    SelfServiceOffer,
    SuggestedAnswer,
    TemplatePage,
    TemplateSort,
    TemplateUseRequest,
    TemplateUseResponse,
    WordFormImport,
} from '@/api/generated/schema'

/**
 * What a list of templates is asked for. The server searches, sorts and pages over every template, so
 * the first page by name starts with the first name of all of them.
 */
export interface TemplateListQuery {
    /** What the name contains, ignoring case. */
    q?: string
    kind?: DocumentTemplateSummary['kind']
    /** Only templates for appointments when true, only the others when false. */
    forAppointments?: boolean
    sort?: TemplateSort
    /** Counted from 0. */
    page?: number
    size?: number
}

/** Reads one page of templates from wherever a screen takes them. */
export type TemplatePages = (query: TemplateListQuery) => Promise<TemplatePage>

/** The weight a replacement for a font of a Word form is drawn in. */
export type FormFontWeight = 'LIGHT' | 'REGULAR'

/**
 * How a font a Word form names is drawn instead.
 *
 * @property family the family, empty for the default font
 * @property size   the size in percent of the form's
 * @property weight the weight, light only for a family that ships a light cut
 */
export interface FormFontChoice {
    family: string
    size: number
    weight: FormFontWeight
}

/** The choices for the fonts of a Word form, by the family the form names. */
export type FormFontChoices = Record<string, FormFontChoice>

/** Which drawing of a Word form a preview shows: in its own fonts, or with the replacements chosen. */
export type FormPreviewVersion = 'original' | 'replaced'

/** The sizes a replaced font may be drawn in, in percent of the form's. */
export const FORM_FONT_SIZE = {min: 50, max: 150, same: 100} as const

/** Whether a replaced font may be drawn at that size: a whole percentage the server takes. */
export function formFontSizeTaken(size: number): boolean {
    return Number.isInteger(size) && size >= FORM_FONT_SIZE.min && size <= FORM_FONT_SIZE.max
}

/**
 * The templates of one owner: a station or an association. Each lists, writes and archives its own
 * templates, says what they can name, reads a text into a letter, draws a draft and keeps the PDF of a
 * PDF template. A station's list also holds the templates of its association, marked as such.
 */
export interface TemplateSource {
    list(archived: boolean, query: TemplateListQuery): Promise<TemplatePage>
    /** Where the first page of a template is served as a picture, drawn without a member. */
    pictureUrl(id: number, size?: number): string
    get(id: number): Promise<DocumentTemplateResponse>
    create(request: DocumentTemplateRequest): Promise<DocumentTemplateResponse>
    update(id: number, request: DocumentTemplateRequest): Promise<DocumentTemplateResponse>
    archive(id: number): Promise<DocumentTemplateResponse>
    restore(id: number): Promise<DocumentTemplateResponse>
    /**
     * Copies a template as a new one of the owner, a station's copy of its association's template
     * included; a name another template carries is counted on.
     *
     * @param name what the copy is called, worded by the screen
     */
    duplicate(id: number, name: string): Promise<DocumentTemplateCopy>
    /** What a template of the owner can name. */
    catalogue(): Promise<PlaceholderCatalogueResponse>
    /** The example day in an own date format, or why the format cannot be printed. */
    checkDateFormat(request: DateFormatCheckRequest): Promise<DateFormatCheck>
    /** Reads a Word or OpenDocument text into a template body, its gaps in brackets as placeholders. */
    importLetter(file: File): Promise<LetterImport>
    /** The fonts a Word form names that the owner cannot draw it in, to choose a replacement for each. */
    formFonts(file: File): Promise<MissingFonts>
    /**
     * Makes a PDF template from a Word form: member data where a label is recognised, a question to the
     * person generating everywhere else.
     *
     * @param fonts how each missing font is drawn, by the family the form names
     */
    importForm(file: File, fonts: FormFontChoices): Promise<WordFormImport>
    /**
     * Draws a Word form to compare its fonts: in its own fonts, with the font files handed in for this
     * drawing only, or with the replacements chosen. Nothing is kept.
     *
     * @param originalFonts a font file for each missing family the original is drawn in, by the family the
     *                      form names
     */
    formPreview(file: File, version: FormPreviewVersion, fonts: FormFontChoices, originalFonts: Readonly<Record<string, File>>): Promise<Blob>
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

/**
 * The longest side of a template's picture on a tile: a portrait page shown across the full width of
 * a tile on a dense screen.
 */
export const TEMPLATE_PICTURE_SIZE = 1024

/**
 * The templates of an owner whose routes start with the prefix: none for the station, `/cluster` for
 * the association.
 */
function sourceAt(prefix: string): TemplateSource {
    const templates = `${prefix}/document-templates`
    const at = (id: number, rest = '') => `${templates}/${id}${rest}`
    const crud = createCrudResource<DocumentTemplateSummary, DocumentTemplateRequest, DocumentTemplateRequest,
        DocumentTemplateResponse, DocumentTemplateResponse>(templates)
    return {
        async list(archived, query) {
            const res = await client.get<TemplatePage>(templates, {params: {archived, ...query}})
            return res.data
        },
        pictureUrl: (id, size = TEMPLATE_PICTURE_SIZE) => at(id, `/picture?size=${size}`),
        get: crud.get,
        create: crud.create,
        update: crud.update,
        async archive(id) {
            const res = await client.post<DocumentTemplateResponse>(at(id, '/archive'))
            return res.data
        },
        async restore(id) {
            const res = await client.post<DocumentTemplateResponse>(at(id, '/restore'))
            return res.data
        },
        async duplicate(id, name) {
            const request: DocumentTemplateCopyRequest = {name}
            const res = await client.post<DocumentTemplateCopy>(at(id, '/duplicate'), request)
            return res.data
        },
        async catalogue() {
            const res = await client.get<PlaceholderCatalogueResponse>(`${prefix}/document-placeholders`)
            return res.data
        },
        async checkDateFormat(request) {
            const res = await client.post<DateFormatCheck>(`${prefix}/document-placeholders/date-format`, request)
            return res.data
        },
        importLetter(file) {
            return uploadFile<LetterImport>(`${prefix}/document-template-import`, {file})
        },
        formFonts(file) {
            return uploadFile<MissingFonts>(`${prefix}/document-template-form-import/fonts`, {file})
        },
        importForm(file, fonts) {
            return uploadFile<WordFormImport>(`${prefix}/document-template-form-import`, {file, fonts: JSON.stringify({fonts})})
        },
        async formPreview(file, version, fonts, originalFonts) {
            const form = new FormData()
            form.append('file', file)
            form.append('version', version)
            form.append('fonts', JSON.stringify({fonts}))
            const handedIn = Object.entries(originalFonts)
            handedIn.forEach(([, font]) => form.append('originalFonts', font))
            form.append('originalFamilies', JSON.stringify(handedIn.map(([family]) => family)))
            const res = await client.post<Blob>(`${prefix}/document-template-form-import/preview`, form,
                {headers: {'Content-Type': null}, responseType: 'blob'})
            return res.data
        },
        async previewDraft(template, templateId, memberId) {
            const res = await client.post<PreviewResponse>(`${prefix}/document-template-preview`, {template, templateId, memberId})
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
export const stationTemplateSource: TemplateSource = sourceAt('')

/** The templates of the association the reader acts for, which all its stations use. */
export const associationTemplateSource: TemplateSource = sourceAt('/cluster')

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

/** One page of the templates a manager can generate documents from: the station's own and its association's. */
export const usableTemplates: TemplatePages = async query => {
    const res = await client.get<TemplatePage>('/document-generation/templates', {params: query})
    return res.data
}

/**
 * What a manager answers when generating one document: the template's questions by their keys, a text,
 * an ISO date, or `true` or `false`. A question left out takes its suggestion.
 */
export type GenerationAnswers = Record<string, string>

/**
 * Draws a document for a member without filing it.
 *
 * @param issuer  the member to issue it instead of the template's issuer, or null to keep the template's
 * @param answers the answers to the template's questions
 */
export async function previewForMember(
    templateId: number, memberId: number, issuer: IssuerChoice | null, answers: GenerationAnswers = {}): Promise<PreviewResponse> {
    const request: ManagerGenerationRequest = {issuer, answers}
    const res = await client.post<PreviewResponse>(`/document-generation/templates/${templateId}/members/${memberId}/preview`, request)
    return res.data
}

/**
 * The questions of a template, each with the answer it suggests for a member.
 *
 * @param issuer the member to issue it instead of the template's issuer, or null to keep the template's
 */
export async function suggestedAnswers(templateId: number, memberId: number, issuer: IssuerChoice | null): Promise<SuggestedAnswer[]> {
    const request: ManagerGenerationRequest = {issuer}
    const res = await client.post<SuggestedAnswer[]>(`/document-generation/templates/${templateId}/members/${memberId}/suggestions`, request)
    return res.data
}

/**
 * Generates a document for a member and files it with them.
 *
 * @param issuer  the member to issue it instead of the template's issuer, or null to keep the template's
 * @param answers the answers to the template's questions
 */
export async function generateForMember(
    templateId: number, memberId: number, issuer: IssuerChoice | null, answers: GenerationAnswers = {}): Promise<GeneratedDocumentResponse> {
    const request: ManagerGenerationRequest = {issuer, answers}
    const res = await client.post<GeneratedDocumentResponse>(`/document-generation/templates/${templateId}/members/${memberId}`, request)
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
export async function previewJob(templateId: number, request: JobPreviewRequest): Promise<BulkPreviewResponse> {
    const res = await client.post<BulkPreviewResponse>(`/document-generation/templates/${templateId}/jobs/preview`, request)
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

/** One page of the documents the station generated from a template, the newest first. */
export async function generationLog(limit: number, offset: number): Promise<GeneratedDocumentEntry[]> {
    const res = await client.get<GeneratedDocumentEntry[]>('/document-generation/log', {params: {limit, offset}})
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
