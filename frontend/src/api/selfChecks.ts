/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {InventoryItem, ItemMetadata, ItemOwnerName, RequiredInventoryItem} from './inventory'

/** Where a task stands. */
export const SelfCheckState = {
    OPEN: 'OPEN',
    SUBMITTED: 'SUBMITTED',
    DONE: 'DONE',
    OVERTAKEN: 'OVERTAKEN',
} as const

export type SelfCheckStateName = (typeof SelfCheckState)[keyof typeof SelfCheckState]

/** What a member may say about one piece of their gear or one empty place in it. */
export const SelfCheckAnswer = {
    HAVE_IT: 'HAVE_IT',
    DO_NOT_HAVE_IT: 'DO_NOT_HAVE_IT',
    TURNED_UP: 'TURNED_UP',
    WRONG_RECORD: 'WRONG_RECORD',
    NEVER_HAD: 'NEVER_HAD',
    HAVE_ONE: 'HAVE_ONE',
} as const

export type SelfCheckAnswerName = (typeof SelfCheckAnswer)[keyof typeof SelfCheckAnswer]

/** Whether a reviewer has settled one answer. */
export type SelfCheckRowStateName = 'OUTSTANDING' | 'TAKEN' | 'REFUSED'

/** What taking one answer would do. */
export type SelfCheckSettlementName =
    | 'CONFIRMS_PIECE'
    | 'RECORDS_NOT_HELD'
    | 'MARKS_FOUND'
    | 'CONFIRMS_GAP'
    | 'NEEDS_RECORD_PUT_RIGHT'
    | 'NEEDS_A_PIECE_NAMED'
    | 'ANCHOR_GONE'

/** What putting the record right would do with the piece that comes off it. */
export type SelfCheckRemovalName = 'NOTHING' | 'BACK_TO_STORE' | 'RETURNED_TO_OWNER' | 'DELETED'

/** What the number a member typed turned out to match. */
export type SelfCheckFindingName = 'NOTHING_TYPED' | 'NO_MATCH' | 'FREE' | 'HELD' | 'SEVERAL' | 'A_CONTAINER'

/** Whether the member said a piece was gone or asked for another size. */
export type SelfCheckRaisedKindName = 'LOSS' | 'EXCHANGE'

export interface SelfCheckSummary {
    id: number
    memberId: number
    memberName: string
    dueOn?: string | null
    state: SelfCheckStateName
    handedOutAt: string
    submittedAt?: string | null
}

export interface SelfCheckRow {
    id: number
    taskId: number
    itemId?: number | null
    inventoryId: number
    slot?: number | null
    answer: SelfCheckAnswerName
    note: string
    typedInternalId?: string | null
    answeredBy?: number | null
    answeredAt: string
    state: SelfCheckRowStateName
    reviewerReason: string
    reviewedBy?: number | null
    reviewedAt?: string | null
}

export interface SelfCheckRaised {
    id: number
    taskId: number
    kind: SelfCheckRaisedKindName
    itemId?: number | null
    movementId?: number | null
    raisedBy?: number | null
    raisedAt: string
}

/**
 * A task as the person answering it reads it.
 *
 * <p>What is not here is the point of it: no free stock, and nothing the numbers they typed matched.
 */
export interface SelfCheckResponse {
    task: SelfCheckSummary
    required: RequiredInventoryItem[]
    assigned: InventoryItem[]
    rows: SelfCheckRow[]
    raised: SelfCheckRaised[]
}

export interface SelfCheckAnswerBody {
    itemId?: number | null
    inventoryId?: number | null
    slot?: number | null
    answer: SelfCheckAnswerName
    note?: string
    typedInternalId?: string | null
}

export async function handOut(memberIds: number[], dueOn?: string | null): Promise<SelfCheckSummary[]> {
    const res = await client.post<SelfCheckSummary[]>('/self-checks', {memberIds, dueOn})
    return res.data
}

export async function mine(): Promise<SelfCheckSummary[]> {
    const res = await client.get<SelfCheckSummary[]>('/self-checks/mine')
    return res.data
}

export async function readTask(id: number): Promise<SelfCheckResponse> {
    const res = await client.get<SelfCheckResponse>(`/self-checks/${id}`)
    return res.data
}

export async function saveAnswers(id: number, answers: SelfCheckAnswerBody[]): Promise<SelfCheckRow[]> {
    const res = await client.put<SelfCheckRow[]>(`/self-checks/${id}/answers`, {answers})
    return res.data
}

export async function submitTask(id: number): Promise<SelfCheckSummary> {
    const res = await client.post<SelfCheckSummary>(`/self-checks/${id}/submit`)
    return res.data
}

export interface SelfCheckMatchedPiece {
    itemId: number
    name: string
    internalId?: string | null
    inventoryName: string
    heldBy?: number | null
    heldByName: string
}

export interface SelfCheckIdentifierMatch {
    finding: SelfCheckFindingName
    typed?: string | null
    pieces: SelfCheckMatchedPiece[]
    containers: string[]
}

export interface SelfCheckReviewRow {
    row: SelfCheckRow
    answeredByName: string
    reviewedByName: string
    item?: InventoryItem | null
    inventoryName: string
    borrowed: boolean
    recordedLost: boolean
    settlement: SelfCheckSettlementName
    removal: SelfCheckRemovalName
    identifier: SelfCheckIdentifierMatch
}

export interface SelfCheckRaisedView {
    raised: SelfCheckRaised
    itemName: string
    raisedByName: string
}

export interface SelfCheckReview {
    task: SelfCheckSummary & {stationId: number; handedOutBy?: number | null; checkId?: number | null}
    memberName: string
    submittedByName: string
    handedOutByName: string
    rows: SelfCheckReviewRow[]
    raised: SelfCheckRaisedView[]
    required: RequiredInventoryItem[]
    assigned: InventoryItem[]
    freeStock: Record<number, InventoryItem[]>
    mayApprove: boolean
    approvalRefusal: string
}

export interface SelfCheckTask {
    id: number
    memberId: number
    memberName: string
    dueOn?: string | null
    state: SelfCheckStateName
    handedOutAt: string
    submittedAt?: string | null
    handedOutByName: string
    checkId?: number | null
}

/** What the member actually holds, as the reviewer names it while putting the record right. */
export interface CorrectRowRequest {
    inventoryId: number
    pickedItemId?: number | null
    sizeId?: number | null
    ownerKind?: ItemOwnerName | null
    internalId?: string | null
    metadata?: ItemMetadata | null
}

export async function listTasks(includeEnded = false): Promise<SelfCheckTask[]> {
    const res = await client.get<SelfCheckTask[]>('/self-check-reviews', {params: {includeEnded}})
    return res.data
}

export async function readReview(id: number): Promise<SelfCheckReview> {
    const res = await client.get<SelfCheckReview>(`/self-check-reviews/${id}`)
    return res.data
}

export async function takeRow(id: number, rowId: number): Promise<SelfCheckReview> {
    const res = await client.post<SelfCheckReview>(`/self-check-reviews/${id}/rows/${rowId}/take`)
    return res.data
}

export async function correctRow(id: number, rowId: number, data: CorrectRowRequest): Promise<SelfCheckReview> {
    const res = await client.post<SelfCheckReview>(`/self-check-reviews/${id}/rows/${rowId}/correct`, data)
    return res.data
}

export async function refuseRow(id: number, rowId: number, reason: string): Promise<SelfCheckReview> {
    const res = await client.post<SelfCheckReview>(`/self-check-reviews/${id}/rows/${rowId}/refuse`, {reason})
    return res.data
}
