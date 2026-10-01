/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    AnswerBody,
    components,
    CorrectRowRequest,
    HandOutSelfChecksRequest,
    HeldReportRequest,
    RefuseRowRequest,
    SelfCheckAnswerRequest,
    SelfCheckRaised,
    SelfCheckResponse,
    SelfCheckReview,
    SelfCheckRow,
    SelfCheckSummary,
    SelfCheckTask,
} from './generated/schema'

export type SelfCheckStateName = components['schemas']['SelfCheckState']

/** Where a task stands. */
export const SelfCheckState = {
    OPEN: 'OPEN',
    SUBMITTED: 'SUBMITTED',
    DONE: 'DONE',
    OVERTAKEN: 'OVERTAKEN',
} as const satisfies Record<SelfCheckStateName, SelfCheckStateName>

export type SelfCheckAnswerName = components['schemas']['SelfCheckAnswer']

/** What a member may say about one piece of their gear or one empty place in it. */
export const SelfCheckAnswer = {
    HAVE_IT: 'HAVE_IT',
    DO_NOT_HAVE_IT: 'DO_NOT_HAVE_IT',
    TURNED_UP: 'TURNED_UP',
    WRONG_RECORD: 'WRONG_RECORD',
    NEVER_HAD: 'NEVER_HAD',
    HAVE_ONE: 'HAVE_ONE',
} as const satisfies Record<SelfCheckAnswerName, SelfCheckAnswerName>

export async function handOut(memberIds: number[], dueOn?: string | null): Promise<SelfCheckSummary[]> {
    const request: HandOutSelfChecksRequest = {memberIds, dueOn}
    const res = await client.post<SelfCheckSummary[]>('/self-checks', request)
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

export async function saveAnswers(id: number, answers: AnswerBody[]): Promise<SelfCheckRow[]> {
    const request: SelfCheckAnswerRequest = {answers}
    const res = await client.put<SelfCheckRow[]>(`/self-checks/${id}/answers`, request)
    return res.data
}

export async function submitTask(id: number): Promise<SelfCheckSummary> {
    const res = await client.post<SelfCheckSummary>(`/self-checks/${id}/submit`)
    return res.data
}

/**
 * Writes a report down without raising it, because the record it names is one the member has just
 * called wrong.
 *
 * <p>It goes out on its own when the station takes that correction, against the piece and the size
 * that are true by then. The answer it hangs on has to be saved first, which is what it hangs on.
 */
export async function holdReport(id: number, data: HeldReportRequest): Promise<SelfCheckRaised> {
    const res = await client.post<SelfCheckRaised>(`/self-checks/${id}/held-reports`, data)
    return res.data
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
    const request: RefuseRowRequest = {reason}
    const res = await client.post<SelfCheckReview>(`/self-check-reviews/${id}/rows/${rowId}/refuse`, request)
    return res.data
}
