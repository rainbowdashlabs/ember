/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
export interface RegistrationCode {
    id: number
    stationId: string
    code?: string
    maxUses: number
    uses: number
    hasUsesLeft: boolean
}

export interface CreateCodeRequest {
    code?: string
    maxUses: number
}

export interface CodeDetail {
    id: number
    stationId: string
    code?: string
    maxUses: number
    uses: number
    groupIds?: number[]
}

export interface SetGroupsRequest {
    groupIds?: number[]
}

const codes = createCrudResource<RegistrationCode, CreateCodeRequest, CreateCodeRequest, CodeDetail>(
    '/registration-codes',
)

export const listCodes = codes.list
export const getCode = codes.get
export const createCode = codes.create
export const deleteCode = codes.remove

export async function getCodeGroups(codeId: number): Promise<number[]> {
    const res = await client.get<number[]>(`/registration-codes/${codeId}/groups`)
    return res.data
}

export async function setCodeGroups(codeId: number, data: SetGroupsRequest): Promise<number[]> {
    const res = await client.put<number[]>(`/registration-codes/${codeId}/groups`, data)
    return res.data
}
