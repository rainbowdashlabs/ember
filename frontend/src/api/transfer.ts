/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface TransferToken {
    token: string
    version: string
}

export interface ImportStartResult {
    stationId: string
    stationName: string
}

export interface ImportProgress {
    stationId: string
    stationName: string
    status: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
    phases: string[]
    completedPhases: number
    currentPhase: string | null
    subTotal: number
    subCompleted: number
    error: string | null
}

export async function createTransferToken(): Promise<TransferToken> {
    const res = await client.post<TransferToken>('/station/transfer/create-token')
    return res.data
}

export async function startImport(token: string): Promise<ImportStartResult> {
    const res = await client.post<ImportStartResult>('/admin/transfer/import', {token})
    return res.data
}

export async function getImportProgress(stationId: string): Promise<ImportProgress> {
    const res = await client.get<ImportProgress>(`/admin/transfer/import/${stationId}/progress`)
    return res.data
}

export interface TransferStatus {
    readOnly: boolean
    targetInstanceUrl: string | null
}

export async function getTransferStatus(): Promise<TransferStatus> {
    const res = await client.get<TransferStatus>('/station/transfer/status')
    return res.data
}

export async function deleteMovedStation(): Promise<{message: string}> {
    const res = await client.post<{message: string}>('/station/manage/delete-moved')
    return res.data
}
