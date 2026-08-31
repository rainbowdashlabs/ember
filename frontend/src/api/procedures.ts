/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource, createScopedCrudResource } from './crud'
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
    /** The appointment this was prepared for, or null when it stands on its own. */
    eventId: number | null
    /** The one occurrence of that appointment, as a calendar date. */
    eventDate: string | null
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
    dependencies: [number, number][]
}

export interface ProcedureDetail {
    procedure: Procedure
    items: ProcedureItem[]
    dependencies: [number, number][]
    assigneeIds: number[]
    assignees: MemberIdentity[]
}

interface TemplateRequest {
    name: string
    description?: string
}

interface TemplateItemRequest {
    title: string
    description?: string
    isPublic?: boolean
    userAssigned?: boolean
}

interface TemplateItemUpdateRequest extends TemplateItemRequest {
    position?: number
}

export interface ProcedureRequest {
    name?: string
    description?: string
    templateId?: number
    /** An instant, not a calendar date. Run what a date field holds through `dateToInstant` first. */
    dueAt?: string
    isPublic?: boolean
    assigneeIds?: number[]
    /** The appointment this is being prepared for. Recorded only together with the date. */
    eventId?: number
    /** The one occurrence of that appointment, as a calendar date. */
    eventDate?: string
}

interface ProcedureUpdateRequest {
    name: string
    description?: string
    dueAt?: string | null
    isPublic?: boolean
}

interface ProcedureItemRequest {
    title: string
    description?: string
    isPublic?: boolean
    userAssigned?: boolean
    position?: number
}

interface ProcedureItemUpdateRequest {
    title?: string
    description?: string
    note?: string
    isPublic?: boolean
    userAssigned?: boolean
    position?: number
}

const templates = createCrudResource<
    ProcedureTemplate,
    TemplateRequest,
    TemplateRequest,
    TemplateDetail
>('/procedure-templates')

const templateItems = createScopedCrudResource<
    ProcedureTemplateItem,
    TemplateItemRequest,
    TemplateItemUpdateRequest
>((templateId: number) => `/procedure-templates/${templateId}/items`)

const procedures = createCrudResource<
    Procedure,
    ProcedureRequest,
    ProcedureUpdateRequest,
    ProcedureDetail
>('/procedures')

const procedureItems = createScopedCrudResource<
    ProcedureItem,
    ProcedureItemRequest,
    ProcedureItemUpdateRequest
>((procedureId: number) => `/procedures/${procedureId}/items`)

// -- Templates --

export const getTemplates = templates.list
export const getTemplate = templates.get
export const createTemplate = templates.create
export const updateTemplate = templates.update
export const archiveTemplate = templates.remove

// -- Template Items --

export const createTemplateItem = templateItems.create
export const updateTemplateItem = templateItems.update
export const deleteTemplateItem = templateItems.remove

// -- Template Dependencies --

export async function setTemplateDependencies(templateId: number, dependencies: number[][]): Promise<void> {
    await client.put(`/procedure-templates/${templateId}/dependencies`, { dependencies })
}

export async function setProcedureDependencies(procedureId: number, dependencies: { itemId: number; dependsOnItemId: number }[]): Promise<void> {
    await client.put(`/procedures/${procedureId}/dependencies`, { dependencies })
}

// -- Procedures --

export async function getProcedures(params?: { status?: string; assignee?: string }): Promise<Procedure[]> {
    return procedures.list(params)
}

/**
 * What has already been prepared for one evening of one appointment.
 *
 * <p>Read before offering to prepare something: a second list for the same evening is a state
 * nobody tidies up, so the caller offers what is already there instead of making another.
 *
 * @param eventId the appointment
 * @param date    the one occurrence of it, as a calendar date
 */
export async function getProceduresForEvent(eventId: number, date: string): Promise<Procedure[]> {
    const res = await client.get<Procedure[]>(`/procedures/for-event/${eventId}`, { params: { date } })
    return res.data
}

export const getProcedure = procedures.get
export const createProcedure = procedures.create
export const updateProcedure = procedures.update
export const deleteProcedure = procedures.remove

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

export const addItem = procedureItems.create
export const editItem = procedureItems.update
export const deleteItem = procedureItems.remove

export async function patchItem(procedureId: number, itemId: number, data: { checked?: boolean; note?: string }): Promise<ProcedureItem> {
    const res = await client.patch<ProcedureItem>(`/procedures/${procedureId}/items/${itemId}`, data)
    return res.data
}
