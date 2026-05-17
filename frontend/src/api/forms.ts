/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    Form,
    FormAnalytics,
    FormListEntry,
    FormQuestion,
    FormQuestionRequest,
    FormRequest,
    FormRestrictions,
    FormResponse,
    FormResponseDetail,
    FormSubmitRequest
} from './types'

// -- Form CRUD --

export async function listForms(): Promise<Form[]> {
    const res = await client.get<Form[]>('/forms')
    return res.data
}

export async function listAvailableForms(): Promise<FormListEntry[]> {
    const res = await client.get<FormListEntry[]>('/forms/available')
    return res.data
}

export async function getForm(id: number): Promise<Form> {
    const res = await client.get<Form>(`/forms/${id}`)
    return res.data
}

export async function createForm(data: FormRequest): Promise<Form> {
    const res = await client.post<Form>('/forms', data)
    return res.data
}

export async function updateForm(id: number, data: FormRequest): Promise<Form> {
    const res = await client.put<Form>(`/forms/${id}`, data)
    return res.data
}

export async function deleteForm(id: number): Promise<void> {
    await client.delete(`/forms/${id}`)
}

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

export async function getAnalytics(formId: number): Promise<FormAnalytics> {
    const res = await client.get<FormAnalytics>(`/forms/${formId}/analytics`)
    return res.data
}

export async function listResponses(formId: number): Promise<FormResponse[]> {
    const res = await client.get<FormResponse[]>(`/forms/${formId}/responses`)
    return res.data
}

export async function getResponseDetail(formId: number, responseId: number): Promise<FormResponseDetail> {
    const res = await client.get<FormResponseDetail>(`/forms/${formId}/responses/${responseId}`)
    return res.data
}
