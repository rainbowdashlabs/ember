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
    /** A station asking the body above it for a piece it does not have. */
    REQUEST: 'REQUEST',
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
    /** Whether the owner of the gear can answer for itself here, which decides who names arrivals. */
    ownerAnswersHere?: boolean
    /** What the piece that set out is called, so a list of movements says which of my things this is. */
    itemName?: string | null
    /** Whether the member still holds it, which is what lets them call the movement off themselves. */
    itemStillWithMember?: boolean
    /** The size being replaced, and the one asked for, which a piece written down starts out as. */
    oldSizeId?: number | null
    newSizeId?: number | null
    reason: string
    createdAt: string
    closedAt?: string | null
    /** Why it was refused or taken back, which the reason it was started does not say. */
    closeReason?: string | null
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
    /** Present when this movement was raised to report gear missing. */
    lossReport?: LossReport | null
}

/**
 * What a report that a piece of gear is gone carries.
 *
 * <p>Two notes with two authors, neither standing in for the other: the member said what happened to them,
 * and the manager said what the station is asking the owner for.
 */
export interface LossReport {
    managerNote?: string | null
    memberNote?: string | null
    memberNoteBy?: MemberIdentity | null
    documentName?: string | null
    documentType?: string | null
}

/** The file attached to a report, fetched with the session so it can be handed to the reader. */
export async function downloadDocument(movementId: number): Promise<Blob> {
    const res = await client.get(`/movements/${movementId}/document`, {responseType: 'blob'})
    return res.data as Blob
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
    /** The arriving piece, where it has never been recorded here and there is nothing to pick. */
    newItem?: NewItemRequest | null
}

/** A piece written down at the moment it arrives. Owner and inventory come from the movement. */
export interface NewItemRequest {
    internalId?: string
    name: string
    sizeId?: number | null
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

/** Asks a member for every piece they hold, one chain per piece. */
export async function returnEverything(memberId: number): Promise<Movement[]> {
    const res = await client.post<Movement[]>('/movements/return-everything', {memberId})
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

/**
 * What is wrong with a chain, named rather than worded.
 *
 * <p>The backend sends the rule that is broken and the frontend supplies the sentence, which is what
 * puts the fault in the reader's language. The same shape answers a refused change.
 */
export interface FlowProblem {
    code: string
    /** What the fault is about where naming it helps, a step's label for instance. */
    detail?: string | null
}

export interface MovementFlow {
    id: number
    name: string
    purpose: MovementPurposeName
    archived: boolean
    /** Flows the body above the station owns are shown and named here, but not edited. */
    ownedByCluster: boolean
    /** What stops this chain from being walked, or null when nothing does. */
    problem?: FlowProblem | null
    steps: MovementFlowStep[]
}

export interface MovementFlowBinding {
    inventoryId?: number | null
    ownerKind: ItemOwnerName
    purpose: MovementPurposeName
    party: MovementPartyName
    flowId: number
}

/**
 * The end of a movement that is not the owner. An issue that fills a shelf and one that dresses a
 * member are different chains, and this is what tells them apart.
 */
export const MovementParty = {
    STORE: 'STORE',
    MEMBER: 'MEMBER',
} as const

export type MovementPartyName = (typeof MovementParty)[keyof typeof MovementParty]

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

/** One chain as it now stands, which is how the editor picks up a change it did not get back whole. */
export async function getFlow(id: number): Promise<MovementFlow> {
    const res = await client.get<MovementFlow>(`/movement-flows/${id}`)
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

/**
 * Retires a flow. It stays readable for the movements that walked it.
 *
 * <p>Answers with the chain as it now stands, which is what every change to a chain does: the editor
 * replaces the one card that changed instead of fetching the page again.
 */
export async function archiveFlow(id: number): Promise<MovementFlow> {
    const res = await client.delete<MovementFlow>(`/movement-flows/${id}`)
    return res.data
}

export async function addStep(flowId: number, data: StepRequest): Promise<MovementFlowStep> {
    const res = await client.post<MovementFlowStep>(`/movement-flows/${flowId}/steps`, data)
    return res.data
}

export async function updateStep(stepId: number, data: StepRequest): Promise<MovementFlow> {
    const res = await client.put<MovementFlow>(`/movement-flow-steps/${stepId}`, data)
    return res.data
}

/** Retires a step. It stays readable for the movements that passed it. */
export async function archiveStep(stepId: number): Promise<MovementFlow> {
    const res = await client.delete<MovementFlow>(`/movement-flow-steps/${stepId}`)
    return res.data
}

/** Puts the steps in the order they are to be walked, the whole order in one call. */
export async function reorderSteps(flowId: number, stepIds: number[]): Promise<MovementFlow> {
    const res = await client.put<MovementFlow>(`/movement-flows/${flowId}/step-order`, {stepIds})
    return res.data
}

export async function listBindings(): Promise<MovementFlowBinding[]> {
    const res = await client.get<MovementFlowBinding[]>('/movement-flow-bindings')
    return res.data
}

export async function bindFlow(data: MovementFlowBinding): Promise<void> {
    await client.put('/movement-flow-bindings', data)
}
