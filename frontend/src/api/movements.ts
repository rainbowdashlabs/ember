/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {ItemCustodyName, ItemOwnerName} from './inventory'
import type {MemberIdentity} from './types'

/** What a movement of gear between two parties is for. */
export const MovementPurpose = {
    ISSUE: 'ISSUE',
    RETURN: 'RETURN',
    EXCHANGE: 'EXCHANGE',
} as const

export type MovementPurposeName = (typeof MovementPurpose)[keyof typeof MovementPurpose]

/** Where a movement stands as a whole, as opposed to which step it is on. */
export const MovementState = {
    OPEN: 'OPEN',
    DONE: 'DONE',
    DECLINED: 'DECLINED',
    CANCELLED: 'CANCELLED',
} as const

export type MovementStateName = (typeof MovementState)[keyof typeof MovementState]

/** The party a step belongs to. */
export const StepActor = {
    MEMBER: 'MEMBER',
    STATION: 'STATION',
    OWNER: 'OWNER',
} as const

export type StepActorName = (typeof StepActor)[keyof typeof StepActor]

/** Which of a movement's two items a step is about. */
export const StepSubject = {
    OUTGOING: 'OUTGOING',
    INCOMING: 'INCOMING',
} as const

export type StepSubjectName = (typeof StepSubject)[keyof typeof StepSubject]

/** How a step came to be acknowledged. */
export const AckKind = {
    CONFIRMED: 'CONFIRMED',
    ASSERTED: 'ASSERTED',
    FORCED: 'FORCED',
} as const

export type AckKindName = (typeof AckKind)[keyof typeof AckKind]

export interface Movement {
    id: number
    purpose: MovementPurposeName
    state: MovementStateName
    memberId?: number | null
    memberName?: string | null
    memberIdentity?: MemberIdentity | null
    inventoryId?: number | null
    inventoryName?: string | null
    currentStepLabel?: string | null
    currentStepActor?: StepActorName | null
    reason: string
    createdAt: string
    closedAt?: string | null
}

export interface MovementStep {
    id: number
    position: number
    label: string
    actor: StepActorName
    subject: StepSubjectName
    custodyAfter: ItemCustodyName
    picksItem: boolean
    archived: boolean
    /** Whether the movement is standing on this step. */
    current: boolean
    /** How it was acknowledged, or null while it is still ahead. */
    ackKind?: AckKindName | null
    acknowledgedByName?: string | null
    acknowledgedAt?: string | null
    note?: string | null
    /** Whether this viewer is the one who may press it. */
    actionable: boolean
}

export interface MovementDetail {
    movement: Movement
    steps: MovementStep[]
}

export interface CreateMovementRequest {
    purpose: MovementPurposeName
    memberId?: number | null
    outgoingItemId?: number | null
    inventoryId?: number | null
    oldSizeId?: number | null
    newSizeId?: number | null
    reason?: string
    pickedItemId?: number | null
}

export interface AcknowledgeStepRequest {
    stepId: number
    note?: string
    pickedItemId?: number | null
}

export async function listMovements(): Promise<Movement[]> {
    const res = await client.get<Movement[]>('/movements')
    return res.data
}

export async function getMovement(id: number): Promise<MovementDetail> {
    const res = await client.get<MovementDetail>(`/movements/${id}`)
    return res.data
}

export async function createMovement(data: CreateMovementRequest): Promise<MovementDetail> {
    const res = await client.post<MovementDetail>('/movements', data)
    return res.data
}

export async function acknowledgeStep(id: number, data: AcknowledgeStepRequest): Promise<MovementDetail> {
    const res = await client.post<MovementDetail>(`/movements/${id}/acknowledge`, data)
    return res.data
}

/** Acknowledges a step on behalf of a party that could have answered and has not. Needs a note. */
export async function forceStep(id: number, data: AcknowledgeStepRequest): Promise<MovementDetail> {
    const res = await client.post<MovementDetail>(`/movements/${id}/force`, data)
    return res.data
}

export async function declineMovement(id: number, reason: string): Promise<MovementDetail> {
    const res = await client.post<MovementDetail>(`/movements/${id}/decline`, {reason})
    return res.data
}

export async function cancelMovement(id: number, reason: string): Promise<MovementDetail> {
    const res = await client.post<MovementDetail>(`/movements/${id}/cancel`, {reason})
    return res.data
}

export async function deleteMovement(id: number): Promise<void> {
    await client.delete(`/movements/${id}`)
}

// -- Flows --

export interface MovementFlowStep {
    id: number
    position: number
    label: string
    actor: StepActorName
    subject: StepSubjectName
    custodyAfter: ItemCustodyName
    picksItem: boolean
    archived: boolean
}

export interface MovementFlow {
    id: number
    name: string
    purpose: MovementPurposeName
    archived: boolean
    /** Flows the body above the station owns are shown and named here, but not edited. */
    ownedByCluster: boolean
    steps: MovementFlowStep[]
}

export interface MovementFlowBinding {
    inventoryId?: number | null
    ownerKind: ItemOwnerName
    purpose: MovementPurposeName
    flowId: number
}

export interface FlowRequest {
    name: string
    purpose: MovementPurposeName
}

export interface StepRequest {
    label: string
    actor: StepActorName
    subject: StepSubjectName
    custodyAfter: ItemCustodyName
    picksItem: boolean
}

export async function listFlows(): Promise<MovementFlow[]> {
    const res = await client.get<MovementFlow[]>('/movement-flows')
    return res.data
}

export async function createFlow(data: FlowRequest): Promise<MovementFlow> {
    const res = await client.post<MovementFlow>('/movement-flows', data)
    return res.data
}

export async function renameFlow(id: number, data: FlowRequest): Promise<MovementFlow> {
    const res = await client.put<MovementFlow>(`/movement-flows/${id}`, data)
    return res.data
}

/** Retires a flow. It stays readable for the movements that walked it. */
export async function archiveFlow(id: number): Promise<void> {
    await client.delete(`/movement-flows/${id}`)
}

export async function addStep(flowId: number, data: StepRequest): Promise<MovementFlowStep> {
    const res = await client.post<MovementFlowStep>(`/movement-flows/${flowId}/steps`, data)
    return res.data
}

export async function updateStep(stepId: number, data: StepRequest): Promise<void> {
    await client.put(`/movement-flow-steps/${stepId}`, data)
}

/** Retires a step. It stays readable for the movements that passed it. */
export async function archiveStep(stepId: number): Promise<void> {
    await client.delete(`/movement-flow-steps/${stepId}`)
}

export async function listBindings(): Promise<MovementFlowBinding[]> {
    const res = await client.get<MovementFlowBinding[]>('/movement-flow-bindings')
    return res.data
}

export async function bindFlow(data: MovementFlowBinding): Promise<void> {
    await client.put('/movement-flow-bindings', data)
}
