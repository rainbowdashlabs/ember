/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {RevocationReason, RevocationStatus, ValidationIndication, ValidationSubIndication} from '@/api/generated/schema'
import {createSealCheck, createTimestampCheck} from '@/test/mocks/sealVerification'
import {certificateName, provingTimestamp, revokedAfterTimestamp, subjectAttribute, verdictOf} from './sealVerdict'

/**
 * The one answer a reader gets about a seal before any detail, and the facts read out of the
 * certificate's subject for it.
 */
describe('verdictOf', () => {
    it('calls a passed seal of this installation sealed here', () => {
        expect(verdictOf(createSealCheck())).toBe('sealedHere')
    })

    it('calls a seal whose bytes changed altered, whoever made it', () => {
        expect(verdictOf(createSealCheck({intact: false}))).toBe('altered')
        expect(verdictOf(createSealCheck({
            issuedHere: false,
            indication: ValidationIndication.TOTAL_FAILED,
            subIndication: ValidationSubIndication.HASH_FAILURE,
        }))).toBe('altered')
    })

    it('calls a stranger\'s seal not issued here, even where the validator passed it', () => {
        expect(verdictOf(createSealCheck({
            issuedHere: false,
            indication: ValidationIndication.INDETERMINATE,
            subIndication: ValidationSubIndication.NOT_ISSUED_HERE,
        }))).toBe('notIssuedHere')
    })

    it('calls anything else undecided unclear', () => {
        expect(verdictOf(createSealCheck({
            indication: ValidationIndication.INDETERMINATE,
            subIndication: ValidationSubIndication.REVOKED_NO_POE,
        }))).toBe('unclear')
    })
})

describe('subjectAttribute', () => {
    it('reads an attribute and undoes the escaping of a comma', () => {
        const subject = 'CN=Wache Nord\\, Zug 2,UID=abc,O=ember.example.org'

        expect(subjectAttribute(subject, 'CN')).toBe('Wache Nord, Zug 2')
        expect(subjectAttribute(subject, 'O')).toBe('ember.example.org')
        expect(subjectAttribute(subject, 'OU')).toBeNull()
    })

    it('names a certificate by its organisation where it has no common name', () => {
        expect(certificateName({subject: 'O=Test Trust,C=DE', serialNumber: '1', sha256Fingerprint: 'AA'})).toBe('Test Trust')
        expect(certificateName(null)).toBeNull()
    })
})

describe('revokedAfterTimestamp', () => {
    const revoked = (revokedAt: string) => createSealCheck({
        revocation: {status: RevocationStatus.REVOKED, revokedAt, reason: RevocationReason.KEY_COMPROMISE},
    })

    it('holds when the key was revoked after the earliest intact timestamp', () => {
        expect(revokedAfterTimestamp(revoked('2026-10-06T00:00:00Z'))).toBe(true)
    })

    it('does not hold when the key was revoked before it, or when nothing proves the time', () => {
        expect(revokedAfterTimestamp(revoked('2026-10-01T00:00:00Z'))).toBe(false)
        expect(revokedAfterTimestamp({...revoked('2026-10-06T00:00:00Z'), timestamps: []})).toBe(false)
    })

    it('takes the earliest intact timestamp as the proving one', () => {
        const check = createSealCheck({
            timestamps: [
                createTimestampCheck({time: '2026-10-07T00:00:00Z'}),
                createTimestampCheck({time: '2026-10-01T00:00:00Z', intact: false}),
                createTimestampCheck({time: '2026-10-05T00:00:00Z'}),
            ],
        })

        expect(provingTimestamp(check)?.time).toBe('2026-10-05T00:00:00Z')
    })
})
