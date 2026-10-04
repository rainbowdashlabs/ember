/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource } from './crud'
import { downloadAuthed } from '@/util/downloadAuthed'
import type {
    EvaluationResponse,
    ProtocolDetailResponse,
    ProtocolItemRequest,
    ProtocolListResponse,
    ProtocolRequest,
    ProtocolRunRequest,
    ProtocolSectionRequest,
    RemoteProtocolDetail,
    RunDetailResponse,
    TestProtocol,
    TestProtocolItem,
    TestProtocolRun,
    TestProtocolRunCheck,
    TestProtocolRunMember,
    TestProtocolSection,
} from './generated/schema'

/**
 * Reads a protocol served by a federation partner. The partner is addressed by its station UUID
 * because a protocol id is only unique within the station that owns it.
 */
export async function getFederatedProtocol(
    stationUid: string,
    protocolId: number,
): Promise<RemoteProtocolDetail> {
    const res = await client.get<RemoteProtocolDetail>(`/federated/${stationUid}/protocols/${protocolId}`)
    return res.data
}

const protocols = createCrudResource<
    TestProtocol,
    ProtocolRequest,
    ProtocolRequest,
    ProtocolDetailResponse,
    TestProtocol
>('/protocols')

const runs = createCrudResource<
    TestProtocolRun,
    ProtocolRunRequest,
    ProtocolRunRequest,
    RunDetailResponse
>('/protocols/runs')

export const getProtocol = protocols.get
export const createProtocol = protocols.create
export const updateProtocol = protocols.update
export const deleteProtocol = protocols.remove

export async function listProtocols(): Promise<ProtocolListResponse> {
    const res = await client.get<ProtocolListResponse>('/protocols')
    return res.data
}

export async function createSection(protocolId: number, data: ProtocolSectionRequest): Promise<TestProtocolSection> {
    const res = await client.post<TestProtocolSection>(`/protocols/${protocolId}/sections`, data)
    return res.data
}

export async function updateSection(id: number, data: ProtocolSectionRequest): Promise<void> {
    await client.put(`/protocols/sections/${id}`, data)
}

export async function deleteSection(id: number): Promise<void> {
    await client.delete(`/protocols/sections/${id}`)
}

export async function createItem(sectionId: number, data: ProtocolItemRequest): Promise<TestProtocolItem> {
    const res = await client.post<TestProtocolItem>(`/protocols/sections/${sectionId}/items`, data)
    return res.data
}

export async function updateItem(id: number, data: ProtocolItemRequest): Promise<void> {
    await client.put(`/protocols/items/${id}`, data)
}

export async function deleteItem(id: number): Promise<void> {
    await client.delete(`/protocols/items/${id}`)
}

/** Puts every section of one level of a protocol into the given order. */
export async function reorderSections(protocolId: number, ids: number[]): Promise<void> {
    await client.put(`/protocols/${protocolId}/sections/order`, {ids})
}

/** Puts every point of a section into the given order. */
export async function reorderItems(sectionId: number, ids: number[]): Promise<void> {
    await client.put(`/protocols/sections/${sectionId}/items/order`, {ids})
}

export const listRuns = runs.list
export const getRun = runs.get
export const updateRun = runs.update
export const deleteRun = runs.remove

export async function createRun(protocolId: number, data: ProtocolRunRequest): Promise<TestProtocolRun> {
    const res = await client.post<TestProtocolRun>(`/protocols/${protocolId}/runs`, data)
    return res.data
}

export async function closeRun(id: number): Promise<TestProtocolRun> {
    const res = await client.post<TestProtocolRun>(`/protocols/runs/${id}/close`)
    return res.data
}

export async function lockMember(runId: number, memberId: number): Promise<TestProtocolRunMember> {
    const res = await client.post<TestProtocolRunMember>(`/protocols/runs/${runId}/members/${memberId}/lock`)
    return res.data
}

export async function unlockMember(runId: number, memberId: number): Promise<TestProtocolRunMember> {
    const res = await client.post<TestProtocolRunMember>(`/protocols/runs/${runId}/members/${memberId}/unlock`)
    return res.data
}

export async function getChecks(runId: number, memberId: number): Promise<TestProtocolRunCheck[]> {
    const res = await client.get<TestProtocolRunCheck[]>(`/protocols/runs/${runId}/members/${memberId}/checks`)
    return res.data
}

export async function saveChecks(runId: number, memberId: number, checks: Record<number, boolean>): Promise<TestProtocolRunCheck[]> {
    const res = await client.put<TestProtocolRunCheck[]>(`/protocols/runs/${runId}/members/${memberId}/checks`, {checks})
    return res.data
}

export async function getSectionsDone(runId: number, memberId: number): Promise<number[]> {
    const res = await client.get<number[]>(`/protocols/runs/${runId}/members/${memberId}/sections-done`)
    return res.data
}

export async function toggleSectionDone(runId: number, memberId: number, sectionId: number): Promise<number[]> {
    const res = await client.post<number[]>(`/protocols/runs/${runId}/members/${memberId}/sections/${sectionId}/toggle-done`)
    return res.data
}

export async function getEvaluation(runId: number): Promise<EvaluationResponse> {
    const res = await client.get<EvaluationResponse>(`/protocols/runs/${runId}/evaluation`)
    return res.data
}

/**
 * Downloads a per-member protocol PDF for the given run through the
 * authenticated client.
 */
export function exportMemberPdf(runId: number, memberId: number): Promise<void> {
    return downloadAuthed(`/protocols/runs/${runId}/members/${memberId}/export`, `protocol-${runId}-member-${memberId}.pdf`)
}

/**
 * Downloads the per-member PDFs bundled as a ZIP for the given run.
 */
export function exportAllZip(runId: number): Promise<void> {
    return downloadAuthed(`/protocols/runs/${runId}/export-all`, `protocol-${runId}-export.zip`)
}

/**
 * Downloads the aggregate evaluation PDF for the given run.
 */
export function evaluationPdf(runId: number): Promise<void> {
    return downloadAuthed(`/protocols/runs/${runId}/evaluation/export`, `protocol-${runId}-evaluation.pdf`)
}

export async function completeMember(runId: number, memberId: number): Promise<TestProtocolRunMember> {
    const res = await client.post<TestProtocolRunMember>(`/protocols/runs/${runId}/members/${memberId}/complete`)
    return res.data
}
