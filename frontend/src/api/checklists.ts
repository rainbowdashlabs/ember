/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
export interface ChecklistSummary {
    id: number
    name: string
    description: string
    memberCount: number
    columnCount: number
    lastRefreshedAt?: string | null
    createdAt: string
}

export interface ChecklistColumnDto {
    id: number
    position: number
    label: string
    description: string
}

export interface ChecklistEntryDto {
    id: number
    memberId: number
    memberName: string
    addedAt: string
    deletedAt?: string | null
    inFilter: boolean
}

export interface ChecklistCellDto {
    id: number
    entryId: number
    columnId: number
    checked: boolean
    note?: string | null
    updatedAt: string
    updatedBy?: number | null
}

export interface ChecklistRestrictionDto {
    userTypes: string[]
    groupIds: number[]
    tagIds: number[]
    memberIds: number[]
    mode: 'AND' | 'OR'
}

export interface ChecklistDetail {
    id: number
    name: string
    description: string
    mode: 'AND' | 'OR'
    createdAt: string
    createdBy?: number | null
    lastRefreshedAt?: string | null
    columns: ChecklistColumnDto[]
    entries: ChecklistEntryDto[]
    cells: ChecklistCellDto[]
    restriction: ChecklistRestrictionDto
}

export interface ChecklistNoteHistoryEntry {
    id: number
    oldNote?: string | null
    newNote?: string | null
    changedBy?: number | null
    changedByName?: string | null
    changedAt: string
}

export interface ChecklistRefreshResult {
    added: number
    alreadyPresent: number
}

export interface ChecklistAddMembersResult {
    added: number
    restored: number
    skipped: number
}

export interface ChecklistBulkSetResult {
    updated: number
}

export interface ChecklistCreateRequest {
    name: string
    description?: string
    columns: { label: string; description?: string }[]
    restriction: ChecklistRestrictionDto
}

export interface ChecklistUpdateRequest {
    name?: string
    description?: string
    restriction?: ChecklistRestrictionDto
}

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
