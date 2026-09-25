/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {apiErrorBody, apiErrorCode, apiErrorMessage, apiErrorStatus} from './apiError'

/**
 * What kind of failure something was, which is what decides what the reader should do about it.
 *
 * <p>The distinction that matters most is between a failure that is somebody's to fix and one that is
 * ours. A missing permission is the station's business and telling the reader to file a bug about it
 * wastes everybody's time; a server that fell over is nobody's business but the operator's, and a
 * reader who is not asked to report it usually will not.
 */
export const FailureKind = {
    /** The request never left, or the network dropped it. */
    OFFLINE: 'OFFLINE',
    /** It left and nothing came back in time. */
    TIMEOUT: 'TIMEOUT',
    /** The session is no longer good for anything. */
    SIGNED_OUT: 'SIGNED_OUT',
    /** The reader may not do this. Their station grants the right, not us. */
    DENIED: 'DENIED',
    /** Whatever was asked for is not there any more. */
    GONE: 'GONE',
    /** Somebody else changed it first. */
    CONFLICT: 'CONFLICT',
    /** Refused for what was sent rather than for who sent it. */
    REJECTED: 'REJECTED',
    /** Too big for what the station is allowed to keep. */
    TOO_LARGE: 'TOO_LARGE',
    /** Asked too often, too quickly. */
    TOO_OFTEN: 'TOO_OFTEN',
    /** Ember broke. */
    SERVER_FAULT: 'SERVER_FAULT',
    /** None of the above, which in practice means Ember broke in a way nobody has named yet. */
    UNKNOWN: 'UNKNOWN',
} as const

export type FailureKindName = (typeof FailureKind)[keyof typeof FailureKind]

/**
 * A failure in the terms a reader can act on.
 *
 * @param kind       what sort of failure it was
 * @param message    what happened, in one sentence
 * @param guidance   what to do about it, which every kind has
 * @param reportable whether this looks like a fault in Ember rather than something the reader or their
 *                   station can put right, which is what decides whether a report is offered
 * @param technical  the server's own words, kept for the report and shown only where a reader asks
 * @param status     the HTTP status, where there was one
 * @param code       which refusal this was, named by the backend, where it named one
 */
export interface Failure {
    kind: FailureKindName
    message: string
    guidance: string
    reportable: boolean
    technical?: string
    status?: number
    /**
     * The name the backend gave this refusal, which leads to the line that threw it.
     *
     * <p>Shown quietly beside the message and carried on the report. A reader cannot act on it and is
     * not asked to; it is there so that quoting it, which is what people do with a short code on a
     * screen, saves whoever reads the report from guessing.
     */
    code?: string
}

/**
 * How a failure is described, which the caller supplies so this stays free of the i18n instance.
 *
 * <p>Passing the translator in rather than reaching for it means this can be tested by handing it a
 * function that returns its own key, and that a component's own `t` is the one that runs.
 */
export type Translate = (key: string, named?: Record<string, unknown>) => string

const KEY = 'failure'

/**
 * Reads a thrown thing and says what it means for the reader.
 *
 * <p>The server's own message is preferred wherever it wrote one, because in this application those are
 * written as prose for exactly this purpose. What is added around it is the part a message cannot carry:
 * whose problem this is and what to do next.
 *
 * @param e the thing that was thrown
 * @param t the translator
 * @return the failure, described
 */
export function describeFailure(e: unknown, t: Translate): Failure {
    const status = apiErrorStatus(e)
    const code = apiErrorCode(e)
    const said = translatedRefusal(code, t) ?? apiErrorMessage(e)
    const kind = kindOf(e, status)
    const technical = apiErrorMessage(e) ?? thrownMessage(e)

    return {
        kind,
        message: messageFor(kind, said, t),
        guidance: guidanceFor(kind, t),
        reportable: kind === FailureKind.SERVER_FAULT
            || kind === FailureKind.UNKNOWN
            || kind === FailureKind.TIMEOUT,
        technical: technical && technical !== messageFor(kind, said, t) ? technical : undefined,
        status,
        code,
    }
}

/**
 * The same failure with a different sentence, where the screen has better words than the server.
 *
 * <p>Everything else is kept: what to do about it, whether it is worth reporting, the server's own
 * words for the report and the code that leads to the line that threw. A screen replacing the whole
 * failure to change its first line loses all of that, which is the mistake this exists to prevent.
 *
 * @param failure what happened
 * @param message what to say about it instead
 */
export function saying(failure: Failure, message: string): Failure {
    return {...failure, message}
}

/**
 * The refusal said in the reader's own language, where we have written it.
 *
 * <p>The backend writes its sentences in English and this product is read in German, so quoting the
 * server verbatim answers a German reader in a language the rest of the screen does not use. A
 * refusal that arrives with a code can be said properly: the code names the refusal, and
 * `refusal.<CODE>` is where its German lives.
 *
 * <p>Where no translation has been written the server's own sentence still shows, which is what
 * makes this safe to adopt one refusal at a time: an English sentence that says what happened beats
 * a German one that says nothing, and it was what a reader saw anyway.
 */
function translatedRefusal(code: string | undefined, t: Translate): string | undefined {
    if (!code) return undefined
    const key = `refusal.${code}`
    const written = t(key)
    return written === key ? undefined : written
}

/**
 * The one line a reader sees: the server's own words wherever it wrote any, and ours otherwise.
 *
 * <p>A refusal is the case where the server's message is the only useful thing there is, because it knows
 * which field was wrong and we do not. Anything the server troubled itself to write is therefore preferred
 * even where the kind of failure could not be worked out, since a sentence about the actual problem beats
 * a general one about none.
 *
 * <p>The single exception is a fault, where what comes back is an exception message rather than a sentence.
 * There ours is shown and the server's is kept for the report, where it is the useful half.
 */
function messageFor(kind: FailureKindName, said: string | undefined, t: Translate): string {
    if (kind === FailureKind.SERVER_FAULT) return t(`${KEY}.${kind}.message`)
    return said ?? t(`${KEY}.${kind}.message`)
}

/**
 * What to do about it. Every kind has an answer, which is the point: a failure a reader can do nothing
 * about at least tells them whose problem it is.
 */
function guidanceFor(kind: FailureKindName, t: Translate): string {
    return t(`${KEY}.${kind}.guidance`)
}

function kindOf(e: unknown, status: number | undefined): FailureKindName {
    if (status === undefined) return withoutAResponse(e)
    if (status === 401) return FailureKind.SIGNED_OUT
    if (status === 403) return FailureKind.DENIED
    if (status === 404) return FailureKind.GONE
    if (status === 409) return FailureKind.CONFLICT
    if (status === 413) return FailureKind.TOO_LARGE
    if (status === 429) return FailureKind.TOO_OFTEN
    if (status === 408 || status === 504) return FailureKind.TIMEOUT
    if (status >= 500) return FailureKind.SERVER_FAULT
    if (status >= 400) return FailureKind.REJECTED
    return FailureKind.UNKNOWN
}

/**
 * A rejection with no response at all, which is either the network or the request giving up.
 *
 * <p>Axios says which in a code rather than a status, and the browser says it in a message. Both are
 * read, because getting this wrong tells somebody with no signal that Ember is broken.
 */
function withoutAResponse(e: unknown): FailureKindName {
    const shape = (e ?? {}) as {code?: string; message?: string};
    const code = shape.code ?? ''
    const message = (shape.message ?? '').toLowerCase()
    if (code === 'ECONNABORTED' || code === 'ETIMEDOUT' || message.includes('timeout')) {
        return FailureKind.TIMEOUT
    }
    if (code === 'ERR_NETWORK' || message.includes('network')) return FailureKind.OFFLINE
    if (typeof navigator !== 'undefined' && navigator.onLine === false) return FailureKind.OFFLINE
    return FailureKind.UNKNOWN
}

function thrownMessage(e: unknown): string | undefined {
    const message = (e as {message?: string} | null | undefined)?.message
    return message?.trim() ? message : undefined
}

/**
 * What the server actually answered, in a form worth attaching to a report.
 *
 * <p>A reader describing what they were doing is the useful half of a report; this is the half they
 * cannot write, and it is gathered here so that every report carries it the same way.
 *
 * @param e the thing that was thrown
 * @return one line naming the status and whatever the server said
 */
export function technicalSummary(e: unknown): string {
    const status = apiErrorStatus(e)
    const body = apiErrorBody(e)
    const parts = [
        status ? `HTTP ${status}` : 'no response',
        body?.error,
        apiErrorMessage(e) ?? thrownMessage(e),
    ].filter(Boolean)
    return parts.join(' · ')
}
