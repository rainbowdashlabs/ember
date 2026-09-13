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

/** How a step came to be acknowledged, or that nobody did and it was set by hand. */
export const AckKind = {
    CONFIRMED: 'CONFIRMED',
    ASSERTED: 'ASSERTED',
    FORCED: 'FORCED',
    CORRECTED: 'CORRECTED',
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
    /** The piece that set out, so a row can be followed to the piece it is about. */
    itemId?: number | null
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
    /** The end that is not the owner: a member, or the station's store. */
    party?: MovementPartyName | null
    /** Whose gear it is, which is what the owner's column of the chain is named after. */
    ownerKind?: ItemOwnerName | null
    /** Which of the two pieces the step it stands on is about, and where that step puts it. */
    currentStepSubject?: StepSubjectName | null
    currentStepCustody?: ItemCustodyName | null
    /** Whether this viewer may acknowledge the step it stands on, which is what puts the button on a row. */
    actionable?: boolean
    /** The arriving piece: promised from the start on a planned hand-out, named halfway on an exchange. */
    incomingItemId?: number | null
    incomingItemName?: string | null
    /** What is written on the piece the row is about. */
    itemInternalId?: string | null
    /** The size the row names: the one asked for where there is one, the one replaced otherwise. */
    itemSizeName?: string | null
    /** The picture the row is drawn with, resolved from the piece's kind and its inventory. */
    icon?: string | null
    color?: string | null
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
    /**
     * The self-check this was raised during, where it was raised during one. It waits for nothing
     * either way: naming the task only records that it happened while the member was answering.
     */
    selfCheckId?: number | null
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

/**
 * Writes down that a member is to get this piece, without handing it over yet.
 *
 * <p>What the assign screens do when the hand-out is planned rather than done at the counter: the
 * piece stays where it is and is spoken for, and the chain carries the handing over.
 */
export async function planHandOut(memberId: number, itemId: number, inventoryId?: number | null):
        Promise<MovementDetail> {
    return createMovement({
        purpose: MovementPurpose.ISSUE,
        memberId,
        pickedItemId: itemId,
        inventoryId: inventoryId ?? null,
    })
}

export async function acknowledgeStep(id: number, data: AcknowledgeStepRequest): Promise<MovementDetail> {
    const res = await client.post<MovementDetail>(`/movements/${id}/acknowledge`, data)
    return res.data
}

/**
 * The movements standing at the member they are for, which is what a check with somebody in the room
 * reads: the piece is on them now, or the next step hands them one.
 */
export async function listAtMember(): Promise<Movement[]> {
    const res = await client.get<Movement[]>('/movements/at-member')
    return res.data
}

/** What a correction is to make true of a movement's pieces, after which the chain follows. */
export interface CorrectMovementRequest {
    outgoing?: ItemCustodyName | null
    incoming?: ItemCustodyName | null
    /** Whether the arriving piece is unhooked, which is what putting a swap back before one was named means. */
    detachArrival?: boolean
    /** The state to close it in, or absent to leave it open on whichever step the corrected world has not reached. */
    closeAs?: MovementStateName | null
    reason: string
}

/**
 * Puts a movement where somebody says it should have been, by saying where its pieces are.
 *
 * <p>There is no status to write: where a movement stands is read off its pieces, so a correction says
 * what is true of them and the chain follows.
 */
export async function correctMovement(id: number, data: CorrectMovementRequest): Promise<MovementDetail> {
    const res = await client.post<MovementDetail>(`/movements/${id}/correct`, data)
    return res.data
}

/** The sheet for the shelf: a row per member, a column per inventory, the sizes in the cells. */
export async function exportPdf(movementIds: number[], extraFieldIds: number[]): Promise<Blob> {
    const res = await client.post('/movements/export', {movementIds, extraFieldIds}, {responseType: 'blob'})
    return res.data as Blob
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

/** One step of the chain a movement would be moved onto, as that chain stands today. */
export interface RechainStep {
    index: number
    label: string
    actor: StepActorName
    subject: StepSubjectName
    custodyAfter: ItemCustodyName
    picksItem: boolean
}

/** What moving a movement onto the chain it belongs on would do, read before it is done. */
export interface RechainPlan {
    movementId: number
    /** The chain it walks now, and what that chain is called. */
    currentFlowId: number | null
    currentFlowName: string | null
    /** The words of the step it stands on, or null where it stands on none. */
    standingOn: string | null
    /** The chain it belongs on. */
    targetFlowId: number
    targetFlowName: string | null
    /** Whether the two are the same, in which case there is nothing to do. */
    alreadyRight: boolean
    steps: RechainStep[]
    /** Where it would stand when exactly one step means what its own means, else null. */
    suggestedIndex: number | null
    certain: boolean
}

/**
 * The chain this movement belongs on, and where it would stand once it is there.
 *
 * <p>Refused where the movement has already ended or where nothing binds a chain to what it is.
 */
export async function rechainPlan(id: number): Promise<RechainPlan> {
    const res = await client.get<RechainPlan>(`/movements/${id}/rechain/plan`)
    return res.data
}

/**
 * Moves a movement onto the chain it belongs on and stands it on the step that was chosen.
 *
 * <p>A null step takes the only one that means what the current step means, and is refused where
 * there is not exactly one. Answers with the movement as it now stands, the same as every other
 * action on it.
 */
export async function rechain(id: number, stepIndex: number | null): Promise<MovementDetail> {
    const res = await client.post<MovementDetail>(`/movements/${id}/rechain`, {stepIndex})
    return res.data
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

/** The chain a movement with these ends would walk, and what it would be about. */
export interface FlowPreview {
    flow: MovementFlow
    ownerKind: ItemOwnerName
    party: MovementPartyName
}

/** What the wizard asks about before it starts anything. */
export interface FlowQuery {
    purpose: MovementPurposeName
    memberId?: number | null
    itemId?: number | null
    inventoryId?: number | null
}

/**
 * The chain a movement would walk, asked before anybody starts one.
 *
 * <p>Answers null where no chain serves the combination, which is a case the wizard shows rather than
 * an error: a station that has not written that chain needs telling, not a red banner.
 */
export async function resolveFlow(query: FlowQuery): Promise<FlowPreview | null> {
    try {
        const res = await client.get<FlowPreview>('/movement-flows/resolve', {params: query})
        return res.data
    } catch (e) {
        if (isNotFound(e)) return null
        throw e
    }
}

function isNotFound(error: unknown): boolean {
    return typeof error === 'object' && error !== null
        && (error as {response?: {status?: number}}).response?.status === 404
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

/** One step of the chain a restore would write, before any of it exists. */
export interface RestorePlanStep {
    index: number
    label: string
    actor: StepActorName
    subject: StepSubjectName
    custodyAfter: ItemCustodyName
    picksItem: boolean
}

/** A movement still walking the chain, which the restore has to put somewhere. */
export interface RestorePlanMovement {
    /** The step the movements stand on, or null for the ones whose step has already gone. */
    stepId: number | null
    /** The words that step carries, or null in the same case. */
    standingOn?: string | null
    /** How many movements stand on it, since one answer moves all of them. */
    movements: number
    /** The step they would land on, or null when that is not certain and somebody has to choose. */
    suggestedIndex: number | null
    certain: boolean
}

/** What a restore would do, read before it is done. */
export interface RestorePlan {
    steps: RestorePlanStep[]
    movements: RestorePlanMovement[]
}

/** Where one movement goes once the chain under it has been replaced. */
export interface FlowStepMapping {
    stepId: number | null
    stepIndex: number
}

/**
 * What restoring this chain would replace it with, and where every open movement would land.
 *
 * <p>Read first and shown, because a movement whose step disappears has to be moved and the answer
 * is not always certain. Guessing on the reader's behalf is what this call exists to avoid.
 */
export async function restorePlan(id: number): Promise<RestorePlan> {
    const res = await client.get<RestorePlan>(`/movement-flows/${id}/restore/plan`)
    return res.data
}

/**
 * Puts a chain back to the preset written for the combination it serves.
 *
 * <p>The binding stays and the steps are replaced, so the answer is the chain as it now stands, the
 * same as every other change to a chain.
 *
 * <p>Every open movement on the chain is named in the mappings, including the ones the plan was
 * certain about, so what the reader saw is what is sent. An empty list is the chain nobody is on.
 */
export async function restoreFlow(id: number, mappings: FlowStepMapping[]): Promise<MovementFlow> {
    const res = await client.post<MovementFlow>(`/movement-flows/${id}/restore`, {mappings})
    return res.data
}

export async function listBindings(): Promise<MovementFlowBinding[]> {
    const res = await client.get<MovementFlowBinding[]>('/movement-flow-bindings')
    return res.data
}

export async function bindFlow(data: MovementFlowBinding): Promise<void> {
    await client.put('/movement-flow-bindings', data)
}
