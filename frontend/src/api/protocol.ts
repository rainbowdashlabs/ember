/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { getItem } from './storage'

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
    sourceStationId: string
}

export interface ProtocolListResponse {
    protocols: TestProtocol[]
    shared: SharedProtocolEntry[]
}

// -- Protocols --

export async function listProtocols(): Promise<ProtocolListResponse> {
    const res = await client.get<ProtocolListResponse>('/protocols')
    return res.data
}

export async function getProtocol(id: number): Promise<ProtocolDetailResponse> {
    const res = await client.get<ProtocolDetailResponse>(`/protocols/${id}`)
    return res.data
}

export async function createProtocol(data: { name: string; description?: string; passThreshold?: number | null }): Promise<TestProtocol> {
    const res = await client.post<TestProtocol>('/protocols', data)
    return res.data
}

export async function updateProtocol(id: number, data: { name: string; description?: string; passThreshold?: number | null }): Promise<TestProtocol> {
    const res = await client.put<TestProtocol>(`/protocols/${id}`, data)
    return res.data
}

export async function deleteProtocol(id: number): Promise<void> {
    await client.delete(`/protocols/${id}`)
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

export async function listRuns(): Promise<TestProtocolRun[]> {
    const res = await client.get<TestProtocolRun[]>('/protocols/runs')
    return res.data
}

export async function createRun(protocolId: number, data: { name: string; testDate?: string; memberIds?: number[]; roleIds?: number[]; groupIds?: number[]; tagIds?: number[] }): Promise<TestProtocolRun> {
    const res = await client.post<TestProtocolRun>(`/protocols/${protocolId}/runs`, data)
    return res.data
}

export async function getRun(id: number): Promise<RunDetailResponse> {
    const res = await client.get<RunDetailResponse>(`/protocols/runs/${id}`)
    return res.data
}

export async function updateRun(id: number, data: { name: string; testDate?: string }): Promise<TestProtocolRun> {
    const res = await client.put<TestProtocolRun>(`/protocols/runs/${id}`, data)
    return res.data
}

export async function closeRun(id: number): Promise<TestProtocolRun> {
    const res = await client.post<TestProtocolRun>(`/protocols/runs/${id}/close`)
    return res.data
}

export async function deleteRun(id: number): Promise<void> {
    await client.delete(`/protocols/runs/${id}`)
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

export function exportMemberPdfUrl(runId: number, memberId: number): string {
    const token = getItem('session_token') ?? ''
    const stationId = getItem('station_id') ?? ''
    return `${client.defaults.baseURL}/protocols/runs/${runId}/members/${memberId}/export?token=${encodeURIComponent(token)}&stationId=${encodeURIComponent(stationId)}`
}

export function exportAllZipUrl(runId: number): string {
    const token = getItem('session_token') ?? ''
    const stationId = getItem('station_id') ?? ''
    return `${client.defaults.baseURL}/protocols/runs/${runId}/export-all?token=${encodeURIComponent(token)}&stationId=${encodeURIComponent(stationId)}`
}

export function evaluationPdfUrl(runId: number): string {
    const token = getItem('session_token') ?? ''
    const stationId = getItem('station_id') ?? ''
    return `${client.defaults.baseURL}/protocols/runs/${runId}/evaluation/export?token=${encodeURIComponent(token)}&stationId=${encodeURIComponent(stationId)}`
}

export async function completeMember(runId: number, memberId: number): Promise<TestProtocolRunMember> {
    const res = await client.post<TestProtocolRunMember>(`/protocols/runs/${runId}/members/${memberId}/complete`)
    return res.data
}
