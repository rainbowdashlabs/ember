/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {MemberIdentity} from './types'

export const FormStatus = {
    DRAFT: 'DRAFT',
    OPEN: 'OPEN',
    CLOSED: 'CLOSED',
} as const

export type FormStatusName = (typeof FormStatus)[keyof typeof FormStatus]

export const QuestionTypes = {
    CHOICE: 'CHOICE',
    TEXT: 'TEXT',
    RATING: 'RATING',
    DATE: 'DATE',
    RANKING: 'RANKING',
    LIKERT: 'LIKERT',
} as const

export type QuestionType = (typeof QuestionTypes)[keyof typeof QuestionTypes]

export const FormPurpose = {
    INTERNAL: 'INTERNAL',
    CONTACT: 'CONTACT',
    POLL: 'POLL',
} as const

export type FormPurposeName = (typeof FormPurpose)[keyof typeof FormPurpose]

/**
 * Whitelist of question types allowed per form purpose. Mirrors
 * {@code FormQuestionType.allowedFor(FormPurpose)} on the backend; the
 * editor hides non-whitelisted types in the question-type picker.
 */
export const QUESTION_TYPES_BY_PURPOSE: Record<FormPurposeName, QuestionType[]> = {
    INTERNAL: [
        QuestionTypes.CHOICE,
        QuestionTypes.TEXT,
        QuestionTypes.RATING,
        QuestionTypes.DATE,
        QuestionTypes.RANKING,
        QuestionTypes.LIKERT,
    ],
    CONTACT: [QuestionTypes.TEXT, QuestionTypes.CHOICE, QuestionTypes.DATE],
    POLL: [
        QuestionTypes.CHOICE,
        QuestionTypes.TEXT,
        QuestionTypes.RATING,
        QuestionTypes.DATE,
        QuestionTypes.RANKING,
        QuestionTypes.LIKERT,
    ],
}

export type MultiLimitType = 'NONE' | 'EQUAL_TO' | 'AT_MOST' | 'AT_LEAST'

export type RatingIcon = 'STAR' | 'NUMBER' | 'HEART' | 'THUMB_UP'

export interface Form {
    id: number
    stationId: string
    title: string
    description: string
    status: FormStatusName
    shuffleQuestions: boolean
    allowEdit: boolean
    forced?: boolean
    startAt?: string | null
    endAt?: string | null
    closedAt?: string | null
    createdBy: number
    createdAt: string
    updatedAt: string
    lastActivityAt: string
    restrictionMode?: string
    restricted?: boolean
    purpose: FormPurposeName
    publicUid: string
    responseCount: number
}

export interface FormListEntry {
    id: number
    stationId: string
    title: string
    description: string
    status: string
    startAt?: string | null
    endAt?: string | null
    responseCount: number
    hasResponded: boolean
    restricted?: boolean
}

export interface FormQuestion {
    id: number
    formId: number
    position: number
    formQuestionType: QuestionType
    title: string
    description: string
    required: boolean
    shuffle: boolean
    config: Record<string, unknown>
}

export interface FormResponse {
    id: number
    formId: number
    /** {@code null} for anonymous CONTACT / POLL submissions. */
    memberId: number | null
    /** {@code null} for anonymous CONTACT / POLL submissions. */
    submittedBy: number | null
    submittedByName?: string | null
    submittedAt: string
    updatedAt: string
    memberIdentity?: MemberIdentity | null
    /** Set when a manager has acknowledged a CONTACT submission. */
    acknowledgedAt?: string | null
    /** Set together with {@code acknowledgedAt} — kept around for backwards compat with code that asks for the id. */
    acknowledgedBy?: number | null
    /** Enriched identity of the acknowledger so the UI can render it via {@code MemberName}. */
    acknowledgedByIdentity?: MemberIdentity | null
}

export interface FormAnswer {
    id: number
    responseId: number
    questionId: number
    value: string
}

export interface FormRequest {
    title: string
    description?: string
    shuffleQuestions?: boolean
    allowEdit?: boolean
    startAt?: string | null
    endAt?: string | null
    purpose?: FormPurposeName
}

export interface FormQuestionRequest {
    questionType: string
    title: string
    description?: string
    required?: boolean
    shuffle?: boolean
    config?: unknown
}

export interface FormRestrictions {
    userTypes: string[]
    groupIds: number[]
    tagIds: number[]
    memberIds?: number[]
    mode?: string
}

export interface FormSubmitRequest {
    answers: Record<number, Record<string, unknown>>
}

export interface FormResponseDetail {
    response: FormResponse | null
    answers: FormAnswer[]
}

export interface FormAnalytics {
    formId: number
    totalResponses: number
    questions: FormQuestionAnalytics[]
    missingResponses: MemberIdentity[]
}

export interface FormQuestionAnalytics {
    questionId: number
    questionType: string
    title: string
    config: Record<string, unknown>
    values: string[]
}

// -- Form CRUD --

const forms = createCrudResource<Form, FormRequest>('/forms')

export async function listForms(purpose?: FormPurposeName): Promise<Form[]> {
    return forms.list(purpose ? {purpose} : undefined)
}

export async function listAvailableForms(): Promise<FormListEntry[]> {
    const res = await client.get<FormListEntry[]>('/forms/available')
    return res.data
}

export interface FormSearchResult {
    publicUid: string
    title: string
    purpose: FormPurposeName
    status: string
}

/**
 * Page-editor picker for POLL_EMBED and FORMS_CTA cells. {@code purpose} is required;
 * empty query returns the most recent forms of the requested purpose so the picker has
 * something to show on first focus.
 */
export async function searchForms(
    purpose: FormPurposeName,
    query?: string,
    limit?: number,
): Promise<FormSearchResult[]> {
    const params: Record<string, string | number> = {purpose}
    if (query) params.q = query
    if (limit) params.limit = limit
    const res = await client.get<FormSearchResult[]>('/forms/search', {params})
    return res.data
}

/** Resolves a single form by its public UUID for picker display. Returns {@code null} when not found. */
export async function getFormPickerByUid(purpose: FormPurposeName, uid: string): Promise<FormSearchResult | null> {
    const res = await client.get<FormSearchResult[]>('/forms/search', {params: {purpose, uid}})
    return res.data[0] ?? null
}

export const getForm = forms.get
export const createForm = forms.create
export const updateForm = forms.update
export const deleteForm = forms.remove

export async function publishForm(id: number): Promise<Form> {
    const res = await client.post<Form>(`/forms/${id}/publish`)
    return res.data
}

export async function closeForm(id: number): Promise<Form> {
    const res = await client.post<Form>(`/forms/${id}/close`)
    return res.data
}

// -- Questions --

export async function getQuestions(formId: number): Promise<FormQuestion[]> {
    const res = await client.get<FormQuestion[]>(`/forms/${formId}/questions`)
    return res.data
}

export async function setQuestions(formId: number, questions: FormQuestionRequest[]): Promise<FormQuestion[]> {
    const res = await client.put<FormQuestion[]>(`/forms/${formId}/questions`, questions)
    return res.data
}

// -- Restrictions --

export async function getRestrictions(formId: number): Promise<FormRestrictions> {
    const res = await client.get<FormRestrictions>(`/forms/${formId}/restrictions`)
    return res.data
}

export async function setRestrictions(formId: number, data: FormRestrictions): Promise<FormRestrictions> {
    const res = await client.put<FormRestrictions>(`/forms/${formId}/restrictions`, data)
    return res.data
}

// -- Responding --

export interface EligibleMembers {
    selfEligible: boolean
    eligibleManagedMemberIds: number[]
}

export async function getEligibleMembers(formId: number): Promise<EligibleMembers> {
    const res = await client.get<EligibleMembers>(`/forms/${formId}/eligible-members`)
    return res.data
}

export async function getMyResponse(formId: number): Promise<FormResponseDetail> {
    const res = await client.get<FormResponseDetail>(`/forms/${formId}/my-response`)
    return res.data
}

export async function submitResponse(formId: number, data: FormSubmitRequest): Promise<FormResponse> {
    const res = await client.post<FormResponse>(`/forms/${formId}/respond`, data)
    return res.data
}

export async function updateResponse(formId: number, data: FormSubmitRequest): Promise<FormResponse> {
    const res = await client.put<FormResponse>(`/forms/${formId}/respond`, data)
    return res.data
}

export async function submitForMember(formId: number, memberId: number, data: FormSubmitRequest): Promise<FormResponse> {
    const res = await client.post<FormResponse>(`/forms/${formId}/respond/${memberId}`, data)
    return res.data
}

export async function updateForMember(formId: number, memberId: number, data: FormSubmitRequest): Promise<FormResponse> {
    const res = await client.put<FormResponse>(`/forms/${formId}/respond/${memberId}`, data)
    return res.data
}

// -- Analytics --

/**
 * The forms analytics endpoints live under three parallel surfaces:
 * - {@code /forms/...} — managers viewing any INTERNAL form (gated by POLL_VIEW_RESULTS).
 * - {@code /pages/polls/forms/...} — page editors viewing a POLL form embedded in a POLL_EMBED
 *   cell (gated by PAGE_EDIT, with a server-side purpose check). CONTACT forms intentionally
 *   have no analytics surface; their submissions are read individually as messages.
 * - {@code /pages/forms/...} — page editors reading the individual submissions of a CONTACT form.
 *
 * Picking the right surface keeps the permission model honest: a user with PAGE_EDIT but no
 * POLL_VIEW_RESULTS can still see analytics for the polls they actually embedded on a page.
 */
export const FormAnalyticsBase = {
    FORMS: '/forms',
    PAGE_POLLS: '/pages/polls/forms',
    PAGE_FORMS: '/pages/forms',
} as const
export type FormAnalyticsBaseName = (typeof FormAnalyticsBase)[keyof typeof FormAnalyticsBase]

export async function getAnalytics(
    formId: number,
    base: FormAnalyticsBaseName = FormAnalyticsBase.FORMS,
): Promise<FormAnalytics> {
    const res = await client.get<FormAnalytics>(`${base}/${formId}/analytics`)
    return res.data
}

export async function listResponses(
    formId: number,
    base: FormAnalyticsBaseName = FormAnalyticsBase.FORMS,
): Promise<FormResponse[]> {
    const res = await client.get<FormResponse[]>(`${base}/${formId}/responses`)
    return res.data
}

export async function getResponseDetail(
    formId: number,
    responseId: number,
    base: FormAnalyticsBaseName = FormAnalyticsBase.FORMS,
): Promise<FormResponseDetail> {
    const res = await client.get<FormResponseDetail>(`${base}/${formId}/responses/${responseId}`)
    return res.data
}

/**
 * Marks a CONTACT-form submission as acknowledged by the calling member. Only available on the
 * page-editor contact-form surface (gated by {@code PAGE_FORMS_VIEW}).
 */
export async function acknowledgeContactResponse(formId: number, responseId: number): Promise<void> {
    await client.post(`/pages/forms/${formId}/responses/${responseId}/acknowledge`)
}
