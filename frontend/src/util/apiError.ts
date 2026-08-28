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
 * Failure text supplied by the backend, or undefined when the response carried none.
 * Callers pick their own localised fallback.
 */
export function apiErrorMessage(e: unknown): string | undefined {
    const data = asApiError(e).response?.data
    return data?.message ?? data?.title
}

/**
 * Failure text supplied by the backend, falling back to the thrown error's own
 * message (network failures, aborted requests, programming errors).
 */
export function errorMessage(e: unknown): string | undefined {
    return apiErrorMessage(e) ?? asApiError(e).message
}
