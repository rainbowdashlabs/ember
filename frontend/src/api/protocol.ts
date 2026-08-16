/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource } from './crud'
import { downloadAuthed } from '@/util/downloadAuthed'

export interface TestProtocol {
    id: number
    stationId: string
    name: string
    description: string
    passThreshold: number | null
    createdAt: string
    updatedAt: string
}

export interface TestProtocolSection {
    id: number
    protocolId: number
    parentId: number | null
    name: string
    description: string
    maxPoints: number | null
    passThreshold: number | null
    position: number
}

export interface TestProtocolItem {
    id: number
    sectionId: number
    label: string
    description: string
    points: number
    position: number
}

export interface TestProtocolRun {
    id: number
    protocolId: number
    stationId: string
    name: string
    testDate: string
    status: 'OPEN' | 'CLOSED'
    createdBy: number
    createdAt: string
}

export interface TestProtocolRunMember {
    id: number
    runId: number
    memberId: number
    lockedBy: number | null
    lockedAt: string | null
    completed: boolean
    totalScore: number
}

export interface TestProtocolRunCheck {
    runMemberId: number
    itemId: number
    checked: boolean
    checkedBy: number | null
    checkedAt: string | null
}

export interface ProtocolDetailResponse {
    protocol: TestProtocol
    sections: TestProtocolSection[]
    items: TestProtocolItem[]
}

/**
 * Reads a protocol served by a federation partner. The partner is addressed by its station UUID
 * because a protocol id is only unique within the station that owns it.
 */
export async function getFederatedProtocol(
    stationUid: string,
    protocolId: number,
): Promise<ProtocolDetailResponse> {
    const res = await client.get<ProtocolDetailResponse>(`/federated/${stationUid}/protocols/${protocolId}`)
    return res.data
}

export interface RunMemberWithProgress {
    member: TestProtocolRunMember
    sectionsDone: number
    sectionsTotal: number
}

export interface RunDetailResponse {
    run: TestProtocolRun
    members: RunMemberWithProgress[]
}

export interface SharedProtocolEntry {
    id: number
    name: string
    description: string | null
    stationName: string
    stationUid: string | null
}

export interface ProtocolListResponse {
    protocols: TestProtocol[]
    shared: SharedProtocolEntry[]
}

interface ProtocolRequest {
    name: string
    description?: string
    passThreshold?: number | null
}

interface RunUpdateRequest {
    name: string
    testDate?: string
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
    TestProtocolRun,
    RunUpdateRequest,
    RunDetailResponse
>('/protocols/runs')

// -- Protocols --

export const getProtocol = protocols.get
export const createProtocol = protocols.create
export const updateProtocol = protocols.update
export const deleteProtocol = protocols.remove

export async function listProtocols(): Promise<ProtocolListResponse> {
    const res = await client.get<ProtocolListResponse>('/protocols')
    return res.data
}

// -- Sections --

export async function createSection(protocolId: number, data: { parentId?: number | null; name: string; description?: string; maxPoints?: number | null; passThreshold?: number | null; position?: number }): Promise<TestProtocolSection> {
    const res = await client.post<TestProtocolSection>(`/protocols/${protocolId}/sections`, data)
    return res.data
}

export async function updateSection(id: number, data: { name: string; description?: string; maxPoints?: number | null; passThreshold?: number | null; position?: number }): Promise<void> {
    await client.put(`/protocols/sections/${id}`, data)
}

export async function deleteSection(id: number): Promise<void> {
    await client.delete(`/protocols/sections/${id}`)
}

// -- Items --

export async function createItem(sectionId: number, data: { label: string; description?: string; points?: number; position?: number }): Promise<TestProtocolItem> {
    const res = await client.post<TestProtocolItem>(`/protocols/sections/${sectionId}/items`, data)
    return res.data
}

export async function updateItem(id: number, data: { label: string; description?: string; points?: number; position?: number }): Promise<void> {
    await client.put(`/protocols/items/${id}`, data)
}

export async function deleteItem(id: number): Promise<void> {
    await client.delete(`/protocols/items/${id}`)
}

// -- Runs --

export const listRuns = runs.list
export const getRun = runs.get
export const updateRun = runs.update
export const deleteRun = runs.remove

export async function createRun(protocolId: number, data: { name: string; testDate?: string; memberIds?: number[]; userTypes?: string[]; groupIds?: number[]; tagIds?: number[] }): Promise<TestProtocolRun> {
    const res = await client.post<TestProtocolRun>(`/protocols/${protocolId}/runs`, data)
    return res.data
}

export async function closeRun(id: number): Promise<TestProtocolRun> {
    const res = await client.post<TestProtocolRun>(`/protocols/runs/${id}/close`)
    return res.data
}

// -- Grading --

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

export interface EvalMemberData {
    memberId: number
    totalScore: number
    sectionScores: Record<number, number>
}

export interface EvaluationResponse {
    protocolName: string
    testDate: string
    sections: TestProtocolSection[]
    sectionMaxPoints: Record<number, number>
    members: EvalMemberData[]
    passThreshold: number | null
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
