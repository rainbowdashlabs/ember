/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {FormPurposeName, PageTarget, QuestionType} from './forms'

export interface PublicFormQuestion {
    id: number
    questionType: QuestionType
    title: string
    description: string
    required: boolean
    /** Whether the options come in a different order for every reader. */
    shuffle: boolean
    /** The key of the page the question stands on. */
    pageKey: string
    config: Record<string, unknown>
    /** Where the page leads per option key picked, for the question that decides it. */
    branch?: Record<string, PageTarget> | null
}

/** One page of a public form, which the browser walks the way the server does on submit. */
export interface PublicFormPage {
    key: string
    title: string
    description: string
    after: PageTarget
}

/**
 * Why a form is not taking answers, in the terms the reader can act on. CLOSED covers a form
 * somebody closed and one whose end date has passed alike.
 */
export const PublicFormState = {
    OPEN: 'OPEN',
    NOT_PUBLISHED: 'NOT_PUBLISHED',
    NOT_OPEN_YET: 'NOT_OPEN_YET',
    CLOSED: 'CLOSED',
} as const

export type PublicFormStateName = (typeof PublicFormState)[keyof typeof PublicFormState]

export interface PublicForm {
    publicUid: string
    title: string
    description: string
    purpose: FormPurposeName
    state: PublicFormStateName
    /** When it stopped taking answers, or nothing while it still does. */
    closedSince?: string | null
    /** Whether the questions of each page come in a different order for every reader. */
    shuffleQuestions: boolean
    /** The pages in their order; empty unless the form is open, like the questions. */
    pages: PublicFormPage[]
    /** Empty unless the form is open: a form nobody can answer hands out no questions. */
    questions: PublicFormQuestion[]
}

export interface PublicFormSubmitRequest {
    answers: Record<number, Record<string, unknown>>
    consentVersion: string
    privacyVersion: string
    tosVersion: string
}

export interface PublicFormSubmitResponse {
    responseId: number
}

export async function getPublicForm(stationUid: string, publicUid: string): Promise<PublicForm> {
    const res = await client.get<PublicForm>(`/public/${stationUid}/forms/${publicUid}`)
    return res.data
}

export async function submitPublicResponse(
    stationUid: string,
    publicUid: string,
    data: PublicFormSubmitRequest,
): Promise<PublicFormSubmitResponse> {
    const res = await client.post<PublicFormSubmitResponse>(
        `/public/${stationUid}/forms/${publicUid}/responses`,
        data,
    )
    return res.data
}

/** A form reached by the link it was sent with. No station stands in the address: the reader holds only the link. */
export async function getSharedForm(token: string): Promise<PublicForm> {
    const res = await client.get<PublicForm>(`/public/shared-form/${token}`)
    return res.data
}

export async function submitSharedResponse(
    token: string,
    data: PublicFormSubmitRequest,
): Promise<PublicFormSubmitResponse> {
    const res = await client.post<PublicFormSubmitResponse>(`/public/shared-form/${token}/responses`, data)
    return res.data
}
