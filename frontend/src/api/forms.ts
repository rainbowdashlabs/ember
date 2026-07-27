/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {
    Form,
    FormAnalytics,
    FormListEntry,
    FormPurposeName,
    FormQuestion,
    FormQuestionRequest,
    FormRequest,
    FormRestrictions,
    FormResponse,
    FormResponseDetail,
    FormSubmitRequest
} from './types'

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
 *
 * Picking the right surface keeps the permission model honest: a user with PAGE_EDIT but no
 * POLL_VIEW_RESULTS can still see analytics for the polls they actually embedded on a page.
 */
export const FormAnalyticsBase = {
    FORMS: '/forms',
    PAGE_POLLS: '/pages/polls/forms',
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
