/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {documentFrom, type DocumentFile} from '@/util/documentFile'
import type {ExportFormat, ExportSeparator} from '@/util/exportFormat'
import {createCrudResource} from './crud'
import {FormQuestionType} from './generated/schema'
import type {
    ClearedFormResponses,
    components,
    FormPurpose,
    FormVisibility,
    EligibleMembers,
    Form,
    FormAnalytics,
    FormAnswerValue,
    FormAnswerValueByType,
    FormDraft,
    FormDraftRequest,
    FormDraftResponse,
    FormDuplicateRequest,
    FormLayout,
    FormLayoutRequest,
    FormListEntry,
    FormPage,
    FormQuestion,
    FormRequest,
    FormResponse,
    FormResponseEntry,
    FormRestrictions,
    FormResultQuery,
    FormSearchResult,
    FormShareLinkResponse,
    FormSubmitRequest,
    FormVisibilityRequest,
    FormVisibilityResponse,
    QuestionAnswerCount,
    ReplaceFormShareLinkRequest,
    FormResponseDetail,
    ResultFieldCondition,
    ResultFilter,
    ResultGrouping,
} from './generated/schema'

/**
 * Whitelist of question types allowed per form purpose, in the order the question-type picker offers
 * them. Mirrors {@code FormQuestionType.allowedFor(FormPurpose)} on the backend; the editor hides
 * non-whitelisted types in the picker.
 */
export const QUESTION_TYPES_BY_PURPOSE: Record<FormPurpose, FormQuestionType[]> = {
    INTERNAL: [
        FormQuestionType.CHOICE,
        FormQuestionType.TEXT,
        FormQuestionType.RATING,
        FormQuestionType.DATE,
        FormQuestionType.RANKING,
        FormQuestionType.LIKERT,
    ],
    CONTACT: [FormQuestionType.TEXT, FormQuestionType.CHOICE, FormQuestionType.DATE],
    POLL: [
        FormQuestionType.CHOICE,
        FormQuestionType.TEXT,
        FormQuestionType.RATING,
        FormQuestionType.DATE,
        FormQuestionType.RANKING,
        FormQuestionType.LIKERT,
    ],
}

/** The answer a question of the given kind takes. */
export type AnswerOf<T extends FormQuestionType> = FormAnswerValueByType[T]

/**
 * Whether an answer is one to a question of the given kind, which is what lets a screen drawing one
 * kind of question read the answer as that kind.
 *
 * @param answer the answer, possibly none at all
 * @param type   the kind of question
 */
export function isAnswerOf<T extends FormQuestionType>(answer: FormAnswerValue | undefined, type: T): answer is AnswerOf<T> {
    return answer?.type === type
}

export type PageTargetKindName = components['schemas']['TargetKind']

/** Where a reader goes from a page: the page below, a chosen page further down, or the end of the form. */
export const PageTargetKind = {
    NEXT: 'NEXT',
    PAGE: 'PAGE',
    SUBMIT: 'SUBMIT',
} as const satisfies Record<PageTargetKindName, PageTargetKindName>

const forms = createCrudResource<Form, FormRequest>('/forms')

export async function listForms(purpose?: FormPurpose): Promise<Form[]> {
    return forms.list(purpose ? {purpose} : undefined)
}

export async function listAvailableForms(): Promise<FormListEntry[]> {
    const res = await client.get<FormListEntry[]>('/forms/available')
    return res.data
}

/**
 * Page-editor picker for POLL_EMBED and FORMS_CTA cells. {@code purpose} is required;
 * empty query returns the most recent forms of the requested purpose so the picker has
 * something to show on first focus.
 */
export async function searchForms(
    purpose: FormPurpose,
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
export async function getFormPickerByUid(purpose: FormPurpose, uid: string): Promise<FormSearchResult | null> {
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

/**
 * Copies a form as a new draft with its settings, pages, questions and restrictions, and without its
 * answers, its link, its dates and its status.
 *
 * @param title what the copy is called
 */
export async function duplicateForm(id: number, title: string): Promise<Form> {
    const request: FormDuplicateRequest = {title}
    const res = await client.post<Form>(`/forms/${id}/duplicate`, request)
    return res.data
}

export async function closeForm(id: number): Promise<Form> {
    const res = await client.post<Form>(`/forms/${id}/close`)
    return res.data
}

/**
 * Throws away every answer the form has collected. The form and its questions stay, so it can be
 * asked again.
 *
 * @returns how many answers were thrown away
 */
export async function clearFormResponses(id: number): Promise<number> {
    const res = await client.delete<ClearedFormResponses>(`/forms/${id}/responses`)
    return res.data.cleared
}

export async function getQuestions(formId: number): Promise<FormQuestion[]> {
    const res = await client.get<FormQuestion[]>(`/forms/${formId}/questions`)
    return res.data
}

/** The pages of a form, in their order. */
export async function getPages(formId: number): Promise<FormPage[]> {
    const res = await client.get<FormPage[]>(`/forms/${formId}/pages`)
    return res.data
}

/**
 * Saves the pages and questions of a form. Every stored question left out of the list is removed, and
 * the answers given to it with it; every stored page left out is removed as well.
 *
 * @returns the pages and questions as stored, in the order they were sent
 */
export async function saveLayout(formId: number, layout: FormLayoutRequest): Promise<FormLayout> {
    const res = await client.put<FormLayout>(`/forms/${formId}/questions`, layout)
    return res.data
}

/**
 * How many answers each question of a form holds and how many name each option, which is what
 * removing a question or an option would throw away.
 */
export async function getQuestionAnswerCounts(formId: number): Promise<QuestionAnswerCount[]> {
    const res = await client.get<QuestionAnswerCount[]>(`/forms/${formId}/questions/answer-counts`)
    return res.data
}

export async function getRestrictions(formId: number): Promise<FormRestrictions> {
    const res = await client.get<FormRestrictions>(`/forms/${formId}/restrictions`)
    return res.data
}

export async function setRestrictions(formId: number, data: FormRestrictions): Promise<FormRestrictions> {
    const res = await client.put<FormRestrictions>(`/forms/${formId}/restrictions`, data)
    return res.data
}

export async function getEligibleMembers(formId: number): Promise<EligibleMembers> {
    const res = await client.get<EligibleMembers>(`/forms/${formId}/eligible-members`)
    return res.data
}

export async function getMyResponse(formId: number): Promise<FormResponseDetail> {
    const res = await client.get<FormResponseDetail>(`/forms/${formId}/my-response`)
    return res.data
}

/**
 * What a member in the caller's care has answered, with an empty detail while they have not answered yet.
 */
export async function getMemberResponse(formId: number, memberId: number): Promise<FormResponseDetail> {
    const res = await client.get<FormResponseDetail>(`/forms/${formId}/respond/${memberId}`)
    return res.data
}

function draftPath(formId: number, memberId: number | null): string {
    return memberId ? `/forms/${formId}/draft/${memberId}` : `/forms/${formId}/draft`
}

/** The draft kept for the reader, or for the member in their care, where there is one. */
export async function getDraft(formId: number, memberId: number | null): Promise<FormDraft | null> {
    const res = await client.get<FormDraftResponse>(draftPath(formId, memberId))
    return res.data.draft
}

/** Keeps what was filled in so far. */
export async function saveDraft(formId: number, memberId: number | null, draft: FormDraftRequest): Promise<void> {
    await client.put(draftPath(formId, memberId), draft)
}

/** Throws the draft away, which is what starting over amounts to. */
export async function discardDraft(formId: number, memberId: number | null): Promise<void> {
    await client.delete(draftPath(formId, memberId))
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

/**
 * The forms analytics endpoints live under three parallel surfaces:
 * - {@code /forms/...} - managers viewing any INTERNAL form (gated by POLL_VIEW_RESULTS).
 * - {@code /pages/polls/forms/...} - page editors viewing a POLL form embedded in a POLL_EMBED
 *   cell (gated by PAGE_EDIT, with a server-side purpose check). CONTACT forms intentionally
 *   have no analytics surface; their submissions are read individually as messages.
 * - {@code /pages/forms/...} - page editors reading the individual submissions of a CONTACT form.
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

export type ResultMatchName = components['schemas']['Match']

/** Whether a member has to be in one of several groups or tags, or in all of them. */
export const ResultMatch = {
    ANY: 'ANY',
    ALL: 'ALL',
} as const satisfies Record<ResultMatchName, ResultMatchName>

export type ResultDimensionName = components['schemas']['Dimension']

/** What the results of a form can be grouped by. */
export const ResultDimension = {
    USER_TYPE: 'USER_TYPE',
    GROUP: 'GROUP',
    TAG: 'TAG',
    FIELD: 'FIELD',
    AGE: 'AGE',
} as const satisfies Record<ResultDimensionName, ResultDimensionName>

/** A condition on one profile field as the results view holds it, every part there and empty where unset. */
export type ResultFieldConditionState = Required<ResultFieldCondition>

/**
 * Which respondents count, as the results view holds it: every condition there, empty where it lets
 * everybody through. The server takes any part of it left out.
 */
export type ResultFilterState = Required<Omit<ResultFilter, 'fields'>> & {fields: ResultFieldConditionState[]}

/** How respondents are split into groups, as the results view holds it. */
export type ResultGroupingState = Required<ResultGrouping>

/** The results of an internal form, filtered and grouped by who answered. */
export async function queryAnalytics(formId: number, query: FormResultQuery): Promise<FormAnalytics> {
    const res = await client.post<FormAnalytics>(`/forms/${formId}/analytics/query`, query)
    return res.data
}

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
): Promise<FormResponseEntry[]> {
    const res = await client.get<FormResponseEntry[]>(`${base}/${formId}/responses`)
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

/**
 * The answers to a form, as a spreadsheet or as a sheet.
 *
 * <p>Built on the server from the same columns for both, so a printed copy and a pasted one cannot
 * say different things.
 */
export async function exportResponses(
    formId: number,
    format: ExportFormat,
    separator: ExportSeparator = 'semicolon',
): Promise<DocumentFile> {
    const res = await client.get(`/forms/${formId}/responses/export`, {
        params: format === 'csv' ? {format, separator} : {format},
        responseType: 'blob',
    })
    return documentFrom(res, `Antworten.${format}`)
}

/**
 * The link a form is sent with, minted the first time it is asked for so a form nobody sends never
 * carries one. Only a form meant to be answered from outside has one.
 */
export async function getFormShareLink(formId: number): Promise<string | null> {
    const res = await client.get<FormShareLinkResponse>(`/forms/${formId}/share-link`)
    return res.data.token
}

/** Replaces the link, ending every copy of the one the form carried. */
export async function replaceFormShareLink(formId: number, currentToken: string | null): Promise<string | null> {
    const request: ReplaceFormShareLinkRequest = {currentToken}
    const res = await client.post<FormShareLinkResponse>(`/forms/${formId}/share-link`, request)
    return res.data.token
}

/** Sets whether a public form answers at its own address or only at the link it was sent with. */
export async function setFormVisibility(
    formId: number,
    visibility: FormVisibility,
): Promise<FormVisibilityResponse> {
    const request: FormVisibilityRequest = {visibility}
    const res = await client.put<FormVisibilityResponse>(`/forms/${formId}/visibility`, request)
    return res.data
}
