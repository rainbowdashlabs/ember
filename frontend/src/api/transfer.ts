/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    components,
    ImportProgressResponse,
    ImportStartResponse,
    MessageResponse,
    TokenResponse,
    TransferStatusResponse,
} from './generated/schema'

export type ImportStatusName = components['schemas']['Status']

/** How far a station import has come, as both import progress answers report it. */
export const ImportStatus = {
    IN_PROGRESS: 'IN_PROGRESS',
    COMPLETED: 'COMPLETED',
    FAILED: 'FAILED',
} as const satisfies Record<ImportStatusName, ImportStatusName>

export async function createTransferToken(): Promise<TokenResponse> {
    const res = await client.post<TokenResponse>('/station/transfer/create-token')
    return res.data
}

export async function startImport(token: string): Promise<ImportStartResponse> {
    const res = await client.post<ImportStartResponse>('/admin/transfer/import', {token})
    return res.data
}

export async function getImportProgress(stationUid: string): Promise<ImportProgressResponse> {
    const res = await client.get<ImportProgressResponse>(`/admin/transfer/import/${stationUid}/progress`)
    return res.data
}

export async function getTransferStatus(): Promise<TransferStatusResponse> {
    const res = await client.get<TransferStatusResponse>('/station/transfer/status')
    return res.data
}

export async function deleteMovedStation(): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/station/manage/delete-moved')
    return res.data
}
