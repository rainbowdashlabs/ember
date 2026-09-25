/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {documentFrom, type DocumentFile} from '@/util/documentFile'
import type {ExportFormat, ExportSeparator} from '@/util/exportFormat'
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
 * How far a form meant for people outside the station reaches. Public means its own address answers,
 * which is what a form on a public page needs; unlisted means the link it was sent with is the only
 * way in, so replacing that link ends every way in that was given out.
 */
export const FormVisibility = {
    PUBLIC: 'PUBLIC',
    UNLISTED: 'UNLISTED',
} as const

export type FormVisibilityName = (typeof FormVisibility)[keyof typeof FormVisibility]

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
    visibility: FormVisibilityName
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
    /** Set together with {@code acknowledgedAt} - kept around for backwards compat with code that asks for the id. */
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

/**
 * The results of a form, counted on the server.
 *
 * `questions` describes each question once; `groups` holds the counted answers per group of
 * respondents. An ungrouped view is a single group holding every response. `groupsOverlap` says a
 * respondent can count in more than one group, so the groups can add up to more than the total.
 */
export interface FormAnalytics {
    formId: number
    /** The responses the filter lets through; every response without a filter. */
    totalResponses: number
    /** The ids of those responses, so the individual answers can follow the filter. */
    responseIds: number[]
    questions: FormQuestionInfo[]
    groups: FormResultGroup[]
    groupsOverlap: boolean
    missingResponses: MemberIdentity[]
}

/** A question as the results view knows it: what it asks and how it is set up. */
export interface FormQuestionInfo {
    questionId: number
    questionType: string
    title: string
    config: Record<string, unknown>
}

/** One group of respondents and what they answered, one tally per question in question order. */
export interface FormResultGroup {
    key: string
    /** What the group is called; empty for the group of every response. */
    label: string
    responseCount: number
    tallies: FormQuestionTally[]
}

/**
 * The counted answers to one question. Only the fields of the question's kind are present: option
 * counts and "other" answers for a choice, counts from one star up for a rating, a score per option
 * for a ranking, an average per statement for a Likert grid (null where nobody rated it), and the
 * answers themselves for text and date questions.
 */
export interface FormQuestionTally {
    questionId: number
    answerCount: number
    optionCounts?: number[]
    otherCount?: number
    ratingCounts?: number[]
    rankingScores?: number[]
    statementAverages?: (number | null)[]
    values?: string[]
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

/** Whether a member has to be in one of several groups or tags, or in all of them. */
export const ResultMatch = {
    ANY: 'ANY',
    ALL: 'ALL',
} as const
export type ResultMatchName = (typeof ResultMatch)[keyof typeof ResultMatch]

/** What the results of a form can be grouped by. */
export const ResultDimension = {
    USER_TYPE: 'USER_TYPE',
    GROUP: 'GROUP',
    TAG: 'TAG',
    FIELD: 'FIELD',
    AGE: 'AGE',
} as const
export type ResultDimensionName = (typeof ResultDimension)[keyof typeof ResultDimension]

/**
 * A condition on one profile field: the answers that count for a choice or yes/no field, or a
 * range for a number field.
 */
export interface ResultFieldCondition {
    fieldId: number
    values?: string[]
    from?: number | null
    to?: number | null
}

/**
 * Which respondents count. Conditions on different attributes must all hold; within groups and tags
 * the match decides between any of them and all of them.
 */
export interface ResultFilter {
    userTypes: string[]
    groupIds: number[]
    groupMatch: ResultMatchName
    tagIds: number[]
    tagMatch: ResultMatchName
    fields: ResultFieldCondition[]
    ageFrom: number | null
    ageTo: number | null
}

/**
 * How to split respondents into groups. `only` limits the grouping to chosen group keys, to compare a
 * few; `bounds` are where the brackets start when grouping by age or a number field.
 */
export interface ResultGrouping {
    by: ResultDimensionName
    fieldId?: number | null
    only: string[]
    bounds: number[]
}

export interface FormResultQuery {
    filter: ResultFilter | null
    groupBy: ResultGrouping | null
}

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
    const res = await client.get<{token: string | null}>(`/forms/${formId}/share-link`)
    return res.data.token
}

/** Replaces the link, ending every copy of the one the form carried. */
export async function replaceFormShareLink(formId: number, currentToken: string | null): Promise<string> {
    const res = await client.post<{token: string}>(`/forms/${formId}/share-link`, {currentToken})
    return res.data.token
}

/** Sets whether a public form answers at its own address or only at the link it was sent with. */
/** One page that puts this form on itself, and how far that page reaches. */
export interface PageUsingForm {
    id: number
    title: string
    visibility: string
}

export interface FormVisibilityResponse {
    form: Form
    /** The pages holding the form, where closing it to its link has just stopped it working on them. */
    stillHeldBy: PageUsingForm[]
}

export async function setFormVisibility(
    formId: number,
    visibility: FormVisibilityName,
): Promise<FormVisibilityResponse> {
    const res = await client.put<FormVisibilityResponse>(`/forms/${formId}/visibility`, {visibility})
    return res.data
}
