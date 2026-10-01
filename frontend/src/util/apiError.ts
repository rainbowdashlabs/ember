/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ErrorResponseWrapper, StepUpChallenge} from '@/api/generated/schema'

/**
 * Error payload the backend sends with a failed request: its error envelope, or the step-up challenge.
 * Any of their fields may be missing, because the body is whichever of the two the server answered with.
 *
 * <p>`title` is the problem-detail envelope of a request that never reached the application. A refusal
 * of answers to a form adds `problems`, one per question; a refusal of a change to groups adds
 * `conflicts`, one per member it was refused for.
 */
export type ApiErrorBody = Partial<ErrorResponseWrapper> & Partial<StepUpChallenge> & {
    title?: string
    problems?: ApiAnswerProblem[]
    conflicts?: ApiGroupConflict[]
}

/** One member a change to groups was refused for, with the groups the rule is about for them. */
export interface ApiGroupConflict {
    memberId: number
    memberName: string
    groups: string[]
}

/** One question an answer to a form was refused at. */
export interface ApiAnswerProblem {
    questionId: number
    pageKey: string | null
    code: string
    message: string
}

/**
 * What a failed request leaves behind, in either of the two shapes the application meets.
 *
 * <p>Axios, which every screen of the station uses, hangs the status and the body on `response`.
 * Nuxt's own fetch, which a page rendered on the server uses, puts them on the error itself as
 * `statusCode` and `data`, and an error handed from the server to the browser keeps only those,
 * because the response it came with does not travel.
 */
interface ApiErrorShape {
    message?: string
    statusCode?: number
    data?: ApiErrorBody
    response?: {
        status?: number
        data?: ApiErrorBody
        _data?: ApiErrorBody
    }
}

function asApiError(e: unknown): ApiErrorShape {
    return (e ?? {}) as ApiErrorShape
}

/**
 * HTTP status of a failed request, or undefined when the rejection carries no response.
 */
export function apiErrorStatus(e: unknown): number | undefined {
    const shape = asApiError(e)
    return shape.response?.status ?? shape.statusCode
}

/**
 * Parsed body of a failed request, or undefined when the rejection carries no response.
 */
export function apiErrorBody(e: unknown): ApiErrorBody | undefined {
    const shape = asApiError(e)
    return shape.response?.data ?? shape.response?._data ?? bodyOf(shape.data)
}

/** A body is an object the server wrote; anything else a fetch left in `data` is not one. */
function bodyOf(data: unknown): ApiErrorBody | undefined {
    return data !== null && typeof data === 'object' ? data as ApiErrorBody : undefined
}

/**
 * The refusal's own name, where the backend gave it one, and undefined otherwise.
 *
 * <p>Worth showing and worth reporting: it is the one part of a failure that leads straight back to the
 * line that produced it, which neither the reader's account of what they did nor the sentence they were
 * shown can do.
 */
export function apiErrorCode(e: unknown): string | undefined {
    return said(apiErrorBody(e)?.code)
}

/**
 * How long a refusal asked the caller to wait, in milliseconds, or undefined when it did not say.
 *
 * <p>Only a number the server actually named counts. A caller that gets nothing here decides its
 * own wait, which is what it would have had to do anyway.
 */
export function retryAfterMillis(e: unknown): number | undefined {
    const seconds = apiErrorBody(e)?.retryAfterSeconds
    return typeof seconds === 'number' && seconds > 0 ? seconds * 1000 : undefined
}

/**
 * Failure text supplied by the backend, or undefined when the response carried none.
 * Callers pick their own localised fallback.
 *
 * <p>Blank text counts as none. A caller writes what it gets straight into its error panel, and a
 * panel holding an empty string renders as nothing at all: the screen would then say a request
 * failed by showing the reader absolutely nothing.
 */
export function apiErrorMessage(e: unknown): string | undefined {
    const data = apiErrorBody(e)
    return said(data?.message) ?? said(data?.title)
}

/** The text where something was actually written, and undefined where it was blank or absent. */
function said(text: string | null | undefined): string | undefined {
    return text?.trim() ? text : undefined
}

/**
 * Failure text supplied by the backend, falling back to the thrown error's own
 * message (network failures, aborted requests, programming errors).
 */
export function errorMessage(e: unknown): string | undefined {
    return apiErrorMessage(e) ?? asApiError(e).message
}
