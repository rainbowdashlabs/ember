/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {PublicForm, PublicFormSubmitRequest, PublicFormSubmitResponse} from './generated/schema'

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
