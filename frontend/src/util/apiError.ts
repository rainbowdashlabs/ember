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

interface ApiErrorShape {
    message?: string
    response?: {
        status?: number
        data?: ApiErrorBody
    }
}

function asApiError(e: unknown): ApiErrorShape {
    return (e ?? {}) as ApiErrorShape
}

/**
 * HTTP status of a failed request, or undefined when the rejection carries no response.
 */
export function apiErrorStatus(e: unknown): number | undefined {
    return asApiError(e).response?.status
}

/**
 * Parsed body of a failed request, or undefined when the rejection carries no response.
 */
export function apiErrorBody(e: unknown): ApiErrorBody | undefined {
    return asApiError(e).response?.data
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
    const data = asApiError(e).response?.data
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
