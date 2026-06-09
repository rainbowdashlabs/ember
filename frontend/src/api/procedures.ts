/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { MemberIdentity } from './types'

// -- Types --

export const ProcedureStatus = {
    OPEN: 'OPEN',
    RESOLVED: 'RESOLVED',
} as const

export type ProcedureStatusName = (typeof ProcedureStatus)[keyof typeof ProcedureStatus]

export interface ProcedureTemplate {
    id: number
    stationId: number
    name: string
    description: string | null
    archived: boolean
    createdBy: number
    createdAt: string
}

export interface ProcedureTemplateItem {
    id: number
    templateId: number
    title: string
    description: string | null
    isPublic: boolean
    userAssigned: boolean
    position: number
}

export interface Procedure {
    id: number
    stationId: number
    templateId: number | null
    name: string
    description: string | null
    isPublic: boolean
    status: ProcedureStatusName
    assignedBy: number
    dueAt: string | null
    createdAt: string
    resolvedAt: string | null
}

export interface ProcedureItem {
    id: number
    procedureId: number
    title: string
    description: string | null
    note: string | null
    isPublic: boolean
    userAssigned: boolean
    position: number
    checked: boolean
    checkedAt: string | null
    checkedBy: number | null
}

export interface TemplateDetail {
    template: ProcedureTemplate
    items: ProcedureTemplateItem[]
    dependencies: number[][]
}

export interface ProcedureDetail {
    procedure: Procedure
    items: ProcedureItem[]
    dependencies: number[][]
    assigneeIds: number[]
    assignees: MemberIdentity[]
}

// -- Templates --

export async function getTemplates(): Promise<ProcedureTemplate[]> {
    const res = await client.get<ProcedureTemplate[]>('/procedure-templates')
    return res.data
}

export async function getTemplate(id: number): Promise<TemplateDetail> {
    const res = await client.get<TemplateDetail>(`/procedure-templates/${id}`)
    return res.data
}

export async function createTemplate(data: { name: string; description?: string }): Promise<ProcedureTemplate> {
    const res = await client.post<ProcedureTemplate>('/procedure-templates', data)
    return res.data
}

export async function updateTemplate(id: number, data: { name: string; description?: string }): Promise<ProcedureTemplate> {
    const res = await client.put<ProcedureTemplate>(`/procedure-templates/${id}`, data)
    return res.data
}

export async function archiveTemplate(id: number): Promise<void> {
    await client.delete(`/procedure-templates/${id}`)
}

// -- Template Items --

export async function createTemplateItem(templateId: number, data: { title: string; description?: string; isPublic?: boolean; userAssigned?: boolean }): Promise<ProcedureTemplateItem> {
    const res = await client.post<ProcedureTemplateItem>(`/procedure-templates/${templateId}/items`, data)
    return res.data
}

export async function updateTemplateItem(templateId: number, itemId: number, data: { title: string; description?: string; isPublic?: boolean; userAssigned?: boolean; position?: number }): Promise<ProcedureTemplateItem> {
    const res = await client.put<ProcedureTemplateItem>(`/procedure-templates/${templateId}/items/${itemId}`, data)
    return res.data
}

export async function deleteTemplateItem(templateId: number, itemId: number): Promise<void> {
    await client.delete(`/procedure-templates/${templateId}/items/${itemId}`)
}

// -- Template Dependencies --

export async function setTemplateDependencies(templateId: number, dependencies: number[][]): Promise<void> {
    await client.put(`/procedure-templates/${templateId}/dependencies`, { dependencies })
}

export async function setProcedureDependencies(procedureId: number, dependencies: { itemId: number; dependsOnItemId: number }[]): Promise<void> {
    await client.put(`/procedures/${procedureId}/dependencies`, { dependencies })
}

// -- Procedures --

export async function getProcedures(params?: { status?: string; assignee?: string }): Promise<Procedure[]> {
    const res = await client.get<Procedure[]>('/procedures', { params })
    return res.data
}

export async function getProcedure(id: number): Promise<ProcedureDetail> {
    const res = await client.get<ProcedureDetail>(`/procedures/${id}`)
    return res.data
}

export async function createProcedure(data: { name: string; description?: string; templateId?: number; dueAt?: string; isPublic?: boolean; assigneeIds?: number[] }): Promise<Procedure> {
    const res = await client.post<Procedure>('/procedures', data)
    return res.data
}

export async function updateProcedure(id: number, data: { name: string; description?: string; dueAt?: string | null; isPublic?: boolean }): Promise<Procedure> {
    const res = await client.put<Procedure>(`/procedures/${id}`, data)
    return res.data
}

export async function deleteProcedure(id: number): Promise<void> {
    await client.delete(`/procedures/${id}`)
}

export async function resolveProcedure(id: number): Promise<Procedure> {
    const res = await client.post<Procedure>(`/procedures/${id}/resolve`)
    return res.data
}

export async function reopenProcedure(id: number): Promise<Procedure> {
    const res = await client.post<Procedure>(`/procedures/${id}/reopen`)
    return res.data
}

// -- Assignees --

export async function addAssignees(id: number, memberIds: number[]): Promise<void> {
    await client.post(`/procedures/${id}/assignees`, { memberIds })
}

export async function removeAssignee(id: number, memberId: number): Promise<void> {
    await client.delete(`/procedures/${id}/assignees/${memberId}`)
}

// -- Procedure Items --

export async function addItem(procedureId: number, data: { title: string; description?: string; isPublic?: boolean; userAssigned?: boolean }): Promise<ProcedureItem> {
    const res = await client.post<ProcedureItem>(`/procedures/${procedureId}/items`, data)
    return res.data
}

export async function editItem(procedureId: number, itemId: number, data: { title?: string; description?: string; note?: string; isPublic?: boolean; userAssigned?: boolean; position?: number }): Promise<ProcedureItem> {
    const res = await client.put<ProcedureItem>(`/procedures/${procedureId}/items/${itemId}`, data)
    return res.data
}

export async function deleteItem(procedureId: number, itemId: number): Promise<void> {
    await client.delete(`/procedures/${procedureId}/items/${itemId}`)
}

export async function patchItem(procedureId: number, itemId: number, data: { checked?: boolean; note?: string }): Promise<ProcedureItem> {
    const res = await client.patch<ProcedureItem>(`/procedures/${procedureId}/items/${itemId}`, data)
    return res.data
}
