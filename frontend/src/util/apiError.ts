/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Error payload the backend sends with a failed request. `message` comes from the
 * plain message envelope, `title` from the problem-detail envelope; `error` names
 * the kind of refusal where the backend raised one of its own, and `category` is
 * only populated by the step-up challenge.
 */
export interface ApiErrorBody {
    message?: string
    title?: string
    error?: string
    category?: string
    /** What the account can prove itself with, named by a step-up refusal. */
    proofs?: string[]
    /**
     * How many seconds to wait before asking again, on a refusal for asking too often.
     *
     * <p>It rides in the body as well as in the `Retry-After` header, because a header is only
     * readable cross-origin when the server says it may be, and a screen that wants to count the
     * wait down should not depend on that.
     */
    retryAfterSeconds?: number
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
function said(text: string | undefined): string | undefined {
    return text?.trim() ? text : undefined
}

/**
 * Failure text supplied by the backend, falling back to the thrown error's own
 * message (network failures, aborted requests, programming errors).
 */
export function errorMessage(e: unknown): string | undefined {
    return apiErrorMessage(e) ?? asApiError(e).message
}
