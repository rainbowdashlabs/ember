/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {documentFrom, type DocumentFile} from '@/util/documentFile'
import type {
    AcknowledgeStepRequest,
    BindingRequest,
    BindingResponse,
    ChosenLanding,
    components,
    CorrectMovementRequest,
    CreateMovementRequest,
    FlowPreview,
    FlowRequest,
    FlowResponse,
    MovementDetail,
    MovementResponse,
    RechainPlan,
    RestorePlan,
    StepRequest,
    StepResponse,
} from './generated/schema'

type Schemas = components['schemas']

export type MovementPurposeName = Schemas['MovementPurpose']

/** What a movement of gear between two parties is for. */
export const MovementPurpose = {
    ISSUE: 'ISSUE',
    RETURN: 'RETURN',
    EXCHANGE: 'EXCHANGE',
    /** A station asking the body above it for a piece it does not have. */
    REQUEST: 'REQUEST',
} as const satisfies Record<MovementPurposeName, MovementPurposeName>

export type MovementStateName = Schemas['MovementState']

/** Where a movement stands as a whole, as opposed to which step it is on. */
export const MovementState = {
    OPEN: 'OPEN',
    DONE: 'DONE',
    DECLINED: 'DECLINED',
    CANCELLED: 'CANCELLED',
} as const satisfies Record<MovementStateName, MovementStateName>

export type StepActorName = Schemas['StepActor']

/** The party a step belongs to. */
export const StepActor = {
    MEMBER: 'MEMBER',
    STATION: 'STATION',
    OWNER: 'OWNER',
} as const satisfies Record<StepActorName, StepActorName>

export type StepSubjectName = Schemas['StepSubject']

/** Which of a movement's two items a step is about. */
export const StepSubject = {
    OUTGOING: 'OUTGOING',
    INCOMING: 'INCOMING',
} as const satisfies Record<StepSubjectName, StepSubjectName>

export type AckKindName = Schemas['AckKind']

/** How a step came to be acknowledged, or that nobody did and it was set by hand. */
export const AckKind = {
    CONFIRMED: 'CONFIRMED',
    ASSERTED: 'ASSERTED',
    FORCED: 'FORCED',
    CORRECTED: 'CORRECTED',
    AUTO_CONFIRMED: 'AUTO_CONFIRMED',
} as const satisfies Record<AckKindName, AckKindName>

export type MovementPartyName = Schemas['MovementParty']

/**
 * The end of a movement that is not the owner. An issue that fills a shelf and one that dresses a
 * member are different chains, and this is what tells them apart.
 */
export const MovementParty = {
    STORE: 'STORE',
    MEMBER: 'MEMBER',
} as const satisfies Record<MovementPartyName, MovementPartyName>

/** The file attached to a report, fetched with the session so it can be handed to the reader. */
export async function downloadDocument(movementId: number): Promise<DocumentFile> {
    const res = await client.get(`/movements/${movementId}/document`, {responseType: 'blob'})
    return documentFrom(res, 'Dokument')
}

export async function listMovements(): Promise<MovementResponse[]> {
    const res = await client.get<MovementResponse[]>('/movements')
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
export async function listAtMember(): Promise<MovementResponse[]> {
    const res = await client.get<MovementResponse[]>('/movements/at-member')
    return res.data
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
export async function exportPdf(movementIds: number[], extraFieldIds: number[]): Promise<DocumentFile> {
    const res = await client.post('/movements/export', {movementIds, extraFieldIds}, {responseType: 'blob'})
    return documentFrom(res, 'Bewegungen.pdf')
}

/** Asks a member for every piece they hold, one chain per piece. */
export async function returnEverything(memberId: number): Promise<MovementResponse[]> {
    const res = await client.post<MovementResponse[]>('/movements/return-everything', {memberId})
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

export async function listFlows(): Promise<FlowResponse[]> {
    const res = await client.get<FlowResponse[]>('/movement-flows')
    return res.data
}

/** One chain as it now stands, which is how the editor picks up a change it did not get back whole. */
export async function getFlow(id: number): Promise<FlowResponse> {
    const res = await client.get<FlowResponse>(`/movement-flows/${id}`)
    return res.data
}

export async function createFlow(data: FlowRequest): Promise<FlowResponse> {
    const res = await client.post<FlowResponse>('/movement-flows', data)
    return res.data
}

export async function renameFlow(id: number, data: FlowRequest): Promise<FlowResponse> {
    const res = await client.put<FlowResponse>(`/movement-flows/${id}`, data)
    return res.data
}

/**
 * Says whether a chain confirms the member's receipt of a piece for them as soon as a movement reaches it.
 * Answers with the chain as it now stands.
 */
export async function setMemberReceipt(id: number, skipMemberReceipt: boolean): Promise<FlowResponse> {
    const res = await client.put<FlowResponse>(`/movement-flows/${id}/member-receipt`, {skipMemberReceipt})
    return res.data
}

/**
 * Retires a flow. It stays readable for the movements that walked it.
 *
 * <p>Answers with the chain as it now stands, which is what every change to a chain does: the editor
 * replaces the one card that changed instead of fetching the page again.
 */
export async function archiveFlow(id: number): Promise<FlowResponse> {
    const res = await client.delete<FlowResponse>(`/movement-flows/${id}`)
    return res.data
}

export async function addStep(flowId: number, data: StepRequest): Promise<StepResponse> {
    const res = await client.post<StepResponse>(`/movement-flows/${flowId}/steps`, data)
    return res.data
}

export async function updateStep(stepId: number, data: StepRequest): Promise<FlowResponse> {
    const res = await client.put<FlowResponse>(`/movement-flow-steps/${stepId}`, data)
    return res.data
}

/** Retires a step. It stays readable for the movements that passed it. */
export async function archiveStep(stepId: number): Promise<FlowResponse> {
    const res = await client.delete<FlowResponse>(`/movement-flow-steps/${stepId}`)
    return res.data
}

/** Puts the steps in the order they are to be walked, the whole order in one call. */
export async function reorderSteps(flowId: number, stepIds: number[]): Promise<FlowResponse> {
    const res = await client.put<FlowResponse>(`/movement-flows/${flowId}/step-order`, {stepIds})
    return res.data
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
export async function restoreFlow(id: number, mappings: ChosenLanding[]): Promise<FlowResponse> {
    const res = await client.post<FlowResponse>(`/movement-flows/${id}/restore`, {mappings})
    return res.data
}

export async function listBindings(): Promise<BindingResponse[]> {
    const res = await client.get<BindingResponse[]>('/movement-flow-bindings')
    return res.data
}

export async function bindFlow(data: BindingRequest): Promise<void> {
    await client.put('/movement-flow-bindings', data)
}
