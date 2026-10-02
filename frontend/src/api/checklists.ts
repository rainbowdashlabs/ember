/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
import type {
    AddMembersResponse,
    BulkSetRequest,
    BulkSetResponse,
    CellResponse,
    CellWriteRequest,
    ChecklistDetailResponse,
    ChecklistSummaryResponse,
    ColumnCreateRequest,
    ColumnResponse,
    ColumnUpdateRequest,
    CreateRequest,
    NoteHistoryEntryResponse,
    RefreshResponse,
    UpdateRequest,
} from './generated/schema'

/**
 * A column being defined in the editor, before it has been saved and given an id and a position.
 * Its order is the order of the list it sits in.
 */
export interface ChecklistColumnDraft {
    label: string
    description: string
}

const checklists = createCrudResource<
    ChecklistSummaryResponse,
    CreateRequest,
    UpdateRequest,
    ChecklistDetailResponse,
    ChecklistDetailResponse
>('/checklist', {updateMethod: 'patch'})

const columns = createScopedCrudResource<
    ColumnResponse,
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

export async function refreshChecklist(id: number): Promise<RefreshResponse> {
    const res = await client.post<RefreshResponse>(`/checklist/${id}/refresh`)
    return res.data
}

export async function reorderColumns(id: number, orderedIds: number[]): Promise<void> {
    await client.put(`/checklist/${id}/columns/reorder`, {orderedIds})
}

export async function addMembers(
    id: number,
    memberIds: number[],
): Promise<AddMembersResponse> {
    const res = await client.post<AddMembersResponse>(`/checklist/${id}/entry`, {memberIds})
    return res.data
}

export async function deleteEntry(id: number, entryId: number): Promise<void> {
    await client.delete(`/checklist/${id}/entry/${entryId}`)
}

export async function writeCell(
    id: number,
    entryId: number,
    columnId: number,
    body: CellWriteRequest,
): Promise<CellResponse> {
    const res = await client.put<CellResponse>(
        `/checklist/${id}/entry/${entryId}/column/${columnId}`,
        body,
    )
    return res.data
}

export async function getNoteHistory(
    id: number,
    entryId: number,
    columnId: number,
): Promise<NoteHistoryEntryResponse[]> {
    const res = await client.get<NoteHistoryEntryResponse[]>(
        `/checklist/${id}/entry/${entryId}/column/${columnId}/note-history`,
    )
    return res.data
}

export async function bulkSetColumn(
    id: number,
    columnId: number,
    body: BulkSetRequest,
): Promise<BulkSetResponse> {
    const res = await client.post<BulkSetResponse>(`/checklist/${id}/column/${columnId}/bulk`, body)
    return res.data
}

export function csvUrl(id: number): string {
    return `${client.defaults.baseURL}/checklist/${id}/export.csv`
}

export function pdfUrl(id: number): string {
    return `${client.defaults.baseURL}/checklist/${id}/export.pdf`
}
