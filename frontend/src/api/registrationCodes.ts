/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {CodeDetail, CreateCodeRequest, RegistrationCode, SetGroupsRequest,} from './types'

export async function listCodes(): Promise<RegistrationCode[]> {
    const res = await client.get<RegistrationCode[]>('/registration-codes')
    return res.data
}

export async function getCode(id: number): Promise<CodeDetail> {
    const res = await client.get<CodeDetail>(`/registration-codes/${id}`)
    return res.data
}

export async function createCode(data: CreateCodeRequest): Promise<RegistrationCode> {
    const res = await client.post<RegistrationCode>('/registration-codes', data)
    return res.data
}

export async function deleteCode(id: number): Promise<void> {
    await client.delete(`/registration-codes/${id}`)
}

export async function getCodeGroups(codeId: number): Promise<number[]> {
    const res = await client.get<number[]>(`/registration-codes/${codeId}/groups`)
    return res.data
}

export async function setCodeGroups(codeId: number, data: SetGroupsRequest): Promise<number[]> {
    const res = await client.put<number[]>(`/registration-codes/${codeId}/groups`, data)
    return res.data
}
