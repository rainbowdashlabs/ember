/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource, createScopedCrudResource, type NoContent } from './crud'
import type {
    AssigneeRequest,
    CreateProcedureRequest,
    DependencyEntry,
    DependencyRequest,
    PatchItemRequest,
    Procedure,
    ProcedureDetail,
    ProcedureItem,
    ProcedureItemRequest,
    ProcedureStatus,
    ProcedureTemplate,
    ProcedureTemplateDetail,
    ProcedureTemplateItem,
    ProcedureTemplateRequest,
    UpdateProcedureRequest,
} from './generated/schema'

/** Which procedures a list asks for: by state, and only the reader's own with `assignee: 'me'`. */
export interface ProcedureListParams {
    status?: ProcedureStatus
    assignee?: 'me'
}

const templates = createCrudResource<
    ProcedureTemplate,
    ProcedureTemplateRequest,
    ProcedureTemplateRequest,
    ProcedureTemplateDetail
>('/procedure-templates')

const templateItems = createScopedCrudResource<
    ProcedureTemplateItem,
    ProcedureItemRequest,
    ProcedureItemRequest,
    ProcedureTemplateItem,
    ProcedureTemplateItem,
    NoContent
>((templateId: number) => `/procedure-templates/${templateId}/items`)

const procedures = createCrudResource<
    Procedure,
    CreateProcedureRequest,
    UpdateProcedureRequest,
    ProcedureDetail
>('/procedures')

const procedureItems = createScopedCrudResource<
    ProcedureItem,
    ProcedureItemRequest,
    ProcedureItemRequest,
    ProcedureItem,
    ProcedureItem,
    NoContent
>((procedureId: number) => `/procedures/${procedureId}/items`)

export const getTemplates = templates.list
export const getTemplate = templates.get
export const createTemplate = templates.create
export const updateTemplate = templates.update
export const archiveTemplate = templates.remove

export const createTemplateItem = templateItems.create
export const updateTemplateItem = templateItems.update
export const deleteTemplateItem = templateItems.remove

/** One step waiting for another, with both sides named, as the screens read and write it. */
export type StepDependency = Required<DependencyEntry>

/**
 * Every dependency between the steps, as the server reads it, from the pairs a detail carries: the
 * step first, the step it waits for second.
 *
 * @param pairs the dependencies as a template or procedure detail sends them
 */
export function dependencyEntries(pairs: readonly number[][]): StepDependency[] {
    return pairs.flatMap(([itemId, dependsOnItemId]) =>
        itemId === undefined || dependsOnItemId === undefined ? [] : [{itemId, dependsOnItemId}])
}

export async function setTemplateDependencies(templateId: number, dependencies: StepDependency[]): Promise<void> {
    const request: DependencyRequest = { dependencies }
    await client.put(`/procedure-templates/${templateId}/dependencies`, request)
}

export async function setProcedureDependencies(procedureId: number, dependencies: StepDependency[]): Promise<void> {
    const request: DependencyRequest = { dependencies }
    await client.put(`/procedures/${procedureId}/dependencies`, request)
}

export async function getProcedures(params?: ProcedureListParams): Promise<Procedure[]> {
    return procedures.list(params ? { ...params } : undefined)
}

/**
 * What has already been prepared for one occurrence of one appointment.
 *
 * <p>Read before offering to prepare something: a second list for the same occurrence is a state
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

/** Hands the procedure to more members. @returns the ids of everybody it is handed to now */
export async function addAssignees(id: number, memberIds: number[]): Promise<number[]> {
    const request: AssigneeRequest = { memberIds }
    const res = await client.post<number[]>(`/procedures/${id}/assignees`, request)
    return res.data
}

/** Takes the procedure away from one member. @returns the ids of everybody it is still handed to */
export async function removeAssignee(id: number, memberId: number): Promise<number[]> {
    const res = await client.delete<number[]>(`/procedures/${id}/assignees/${memberId}`)
    return res.data
}

export const addItem = procedureItems.create
export const editItem = procedureItems.update
export const deleteItem = procedureItems.remove

/**
 * Ticks or unticks a step, or writes its note.
 *
 * @returns every step of the procedure as it now stands
 */
export async function patchItem(procedureId: number, itemId: number, data: PatchItemRequest): Promise<ProcedureItem[]> {
    const res = await client.patch<ProcedureItem[]>(`/procedures/${procedureId}/items/${itemId}`, data)
    return res.data
}
