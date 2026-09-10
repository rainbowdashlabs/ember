/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {describeFailure, FailureKind, technicalSummary} from './failure'

/** Returns the key, so a test can see which message was chosen without depending on its wording. */
const t = (key: string) => key

function rejected(status: number, body?: Record<string, unknown>) {
    return {response: {status, data: body ?? {}}}
}

describe('describeFailure', () => {
    it('reads the kind off the status', () => {
        expect(describeFailure(rejected(401), t).kind).toBe(FailureKind.SIGNED_OUT)
        expect(describeFailure(rejected(403), t).kind).toBe(FailureKind.DENIED)
        expect(describeFailure(rejected(404), t).kind).toBe(FailureKind.GONE)
        expect(describeFailure(rejected(409), t).kind).toBe(FailureKind.CONFLICT)
        expect(describeFailure(rejected(413), t).kind).toBe(FailureKind.TOO_LARGE)
        expect(describeFailure(rejected(429), t).kind).toBe(FailureKind.TOO_OFTEN)
        expect(describeFailure(rejected(400), t).kind).toBe(FailureKind.REJECTED)
        expect(describeFailure(rejected(422), t).kind).toBe(FailureKind.REJECTED)
        expect(describeFailure(rejected(500), t).kind).toBe(FailureKind.SERVER_FAULT)
        expect(describeFailure(rejected(503), t).kind).toBe(FailureKind.SERVER_FAULT)
        expect(describeFailure(rejected(504), t).kind).toBe(FailureKind.TIMEOUT)
    })

    /**
     * The distinction the whole thing exists for. Telling somebody to file a bug because their station
     * never gave them a permission sends them to strangers who cannot help.
     */
    it('does not offer a report for what the reader or their station can put right', () => {
        expect(describeFailure(rejected(403), t).reportable).toBe(false)
        expect(describeFailure(rejected(401), t).reportable).toBe(false)
        expect(describeFailure(rejected(404), t).reportable).toBe(false)
        expect(describeFailure(rejected(409), t).reportable).toBe(false)
        expect(describeFailure(rejected(413), t).reportable).toBe(false)
        expect(describeFailure(rejected(429), t).reportable).toBe(false)
        expect(describeFailure(rejected(400), t).reportable).toBe(false)
    })

    it('offers a report where it looks like our fault', () => {
        expect(describeFailure(rejected(500), t).reportable).toBe(true)
        expect(describeFailure(rejected(504), t).reportable).toBe(true)
        expect(describeFailure({}, t).reportable).toBe(true)
    })

    /**
     * A refusal knows which field was wrong and we do not, so its own words win. A fault answers with a
     * class name, which is not a sentence, so ours do.
     */
    it('prefers the server wording for a refusal and ours for a fault', () => {
        const refused = describeFailure(rejected(400, {message: 'Der Ordner fehlt'}), t)
        expect(refused.message).toBe('Der Ordner fehlt')

        const broke = describeFailure(rejected(500, {message: 'NullPointerException'}), t)
        expect(broke.message).toBe('failure.SERVER_FAULT.message')
        expect(broke.technical).toBe('NullPointerException')
    })

    it('says nothing technical where the server said nothing worth keeping', () => {
        expect(describeFailure(rejected(400, {message: 'Der Ordner fehlt'}), t).technical).toBeUndefined()
    })

    /** Every kind has to say what to do about it, or the reader is back to guessing. */
    it('carries a message and guidance for every kind', () => {
        for (const [kind, thrown] of Object.entries(oneOfEach())) {
            const described = describeFailure(thrown, t)
            expect(described.kind, kind).toBe(kind)
            expect(described.message, kind).toBeTruthy()
            expect(described.guidance, kind).toBeTruthy()
        }
    })

    /** Somebody with no signal must not be told that Ember is broken. */
    it('knows a dropped connection from a broken server', () => {
        expect(describeFailure({code: 'ERR_NETWORK', message: 'Network Error'}, t).kind)
            .toBe(FailureKind.OFFLINE)
        expect(describeFailure({code: 'ERR_NETWORK'}, t).reportable).toBe(false)
        expect(describeFailure({code: 'ECONNABORTED', message: 'timeout of 5000ms exceeded'}, t).kind)
            .toBe(FailureKind.TIMEOUT)
    })

    it('keeps the status where there was one', () => {
        expect(describeFailure(rejected(418), t).status).toBe(418)
        expect(describeFailure({}, t).status).toBeUndefined()
    })
})

describe('technicalSummary', () => {
    it('names the status and whatever the server said', () => {
        expect(technicalSummary(rejected(500, {message: 'NullPointerException'})))
            .toBe('HTTP 500 · NullPointerException')
        expect(technicalSummary(rejected(403, {error: 'ForbiddenResponse', message: 'Kein Zugriff'})))
            .toBe('HTTP 403 · ForbiddenResponse · Kein Zugriff')
    })

    it('says so where nothing came back at all', () => {
        expect(technicalSummary({message: 'Network Error'})).toBe('no response · Network Error')
    })
})

/** One thrown thing per kind, so the loop above covers all of them and not only the ones with a status. */
function oneOfEach(): Record<string, unknown> {
    return {
        [FailureKind.OFFLINE]: {code: 'ERR_NETWORK', message: 'Network Error'},
        [FailureKind.TIMEOUT]: {code: 'ECONNABORTED', message: 'timeout of 5000ms exceeded'},
        [FailureKind.SIGNED_OUT]: rejected(401),
        [FailureKind.DENIED]: rejected(403),
        [FailureKind.GONE]: rejected(404),
        [FailureKind.CONFLICT]: rejected(409),
        [FailureKind.REJECTED]: rejected(400),
        [FailureKind.TOO_LARGE]: rejected(413),
        [FailureKind.TOO_OFTEN]: rejected(429),
        [FailureKind.SERVER_FAULT]: rejected(500),
        [FailureKind.UNKNOWN]: {},
    }
}
