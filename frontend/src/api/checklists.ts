/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    ChecklistAddMembersResult,
    ChecklistBulkSetResult,
    ChecklistCellDto,
    ChecklistColumnDto,
    ChecklistCreateRequest,
    ChecklistDetail,
    ChecklistNoteHistoryEntry,
    ChecklistRefreshResult,
    ChecklistSummary,
    ChecklistUpdateRequest,
} from './types'

export async function listChecklists(): Promise<ChecklistSummary[]> {
    const res = await client.get<ChecklistSummary[]>('/checklist')
    return res.data
}

export async function getChecklist(id: number): Promise<ChecklistDetail> {
    const res = await client.get<ChecklistDetail>(`/checklist/${id}`)
    return res.data
}

export async function createChecklist(body: ChecklistCreateRequest): Promise<ChecklistDetail> {
    const res = await client.post<ChecklistDetail>('/checklist', body)
    return res.data
}

export async function updateChecklist(id: number, body: ChecklistUpdateRequest): Promise<ChecklistDetail> {
    const res = await client.patch<ChecklistDetail>(`/checklist/${id}`, body)
    return res.data
}

export async function deleteChecklist(id: number): Promise<void> {
    await client.delete(`/checklist/${id}`)
}

export async function refreshChecklist(id: number): Promise<ChecklistRefreshResult> {
    const res = await client.post<ChecklistRefreshResult>(`/checklist/${id}/refresh`)
    return res.data
}

export async function addColumn(
    id: number,
    body: {label: string; description?: string; position?: number},
): Promise<ChecklistColumnDto> {
    const res = await client.post<ChecklistColumnDto>(`/checklist/${id}/column`, body)
    return res.data
}

export async function updateColumn(
    id: number,
    columnId: number,
    body: {label?: string; description?: string; position?: number},
): Promise<ChecklistColumnDto> {
    const res = await client.patch<ChecklistColumnDto>(`/checklist/${id}/column/${columnId}`, body)
    return res.data
}

export async function deleteColumn(id: number, columnId: number): Promise<void> {
    await client.delete(`/checklist/${id}/column/${columnId}`)
}

export async function reorderColumns(id: number, orderedIds: number[]): Promise<void> {
    await client.put(`/checklist/${id}/columns/reorder`, {orderedIds})
}

export async function addMembers(
    id: number,
    memberIds: number[],
): Promise<ChecklistAddMembersResult> {
    const res = await client.post<ChecklistAddMembersResult>(`/checklist/${id}/entry`, {memberIds})
    return res.data
}

export async function deleteEntry(id: number, entryId: number): Promise<void> {
    await client.delete(`/checklist/${id}/entry/${entryId}`)
}

export async function writeCell(
    id: number,
    entryId: number,
    columnId: number,
    body: {checked: boolean; note?: string | null},
): Promise<ChecklistCellDto> {
    const res = await client.put<ChecklistCellDto>(
        `/checklist/${id}/entry/${entryId}/column/${columnId}`,
        body,
    )
    return res.data
}

export async function getNoteHistory(
    id: number,
    entryId: number,
    columnId: number,
): Promise<ChecklistNoteHistoryEntry[]> {
    const res = await client.get<ChecklistNoteHistoryEntry[]>(
        `/checklist/${id}/entry/${entryId}/column/${columnId}/note-history`,
    )
    return res.data
}

export async function bulkSetColumn(
    id: number,
    columnId: number,
    body: {entryIds: number[]; checked: boolean},
): Promise<ChecklistBulkSetResult> {
    const res = await client.post<ChecklistBulkSetResult>(`/checklist/${id}/column/${columnId}/bulk`, body)
    return res.data
}

export function csvUrl(id: number): string {
    return `${client.defaults.baseURL}/checklist/${id}/export.csv`
}

export function pdfUrl(id: number): string {
    return `${client.defaults.baseURL}/checklist/${id}/export.pdf`
}
