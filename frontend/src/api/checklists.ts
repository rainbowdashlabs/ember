/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
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

interface ColumnCreateRequest {
    label: string
    description?: string
    position?: number
}

interface ColumnUpdateRequest {
    label?: string
    description?: string
    position?: number
}

const checklists = createCrudResource<
    ChecklistSummary,
    ChecklistCreateRequest,
    ChecklistUpdateRequest,
    ChecklistDetail,
    ChecklistDetail
>('/checklist', {updateMethod: 'patch'})

const columns = createScopedCrudResource<
    ChecklistColumnDto,
    ColumnCreateRequest,
    ColumnUpdateRequest
>((id: number) => `/checklist/${id}/column`, {updateMethod: 'patch'})

export const listChecklists = checklists.list
export const getChecklist = checklists.get
export const createChecklist = checklists.create
export const updateChecklist = checklists.update
export const deleteChecklist = checklists.remove

export const addColumn = columns.create
export const updateColumn = columns.update
export const deleteColumn = columns.remove

export async function refreshChecklist(id: number): Promise<ChecklistRefreshResult> {
    const res = await client.post<ChecklistRefreshResult>(`/checklist/${id}/refresh`)
    return res.data
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
