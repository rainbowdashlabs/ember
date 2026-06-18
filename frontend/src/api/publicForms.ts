/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {FormPurposeName, QuestionType} from './types'

export interface PublicFormQuestion {
    id: number
    questionType: QuestionType
    title: string
    description: string
    required: boolean
    config: Record<string, unknown>
}

export interface PublicForm {
    publicUid: string
    title: string
    description: string
    purpose: FormPurposeName
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
