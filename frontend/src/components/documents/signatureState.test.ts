/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {RequestState, type SignatureSummary} from '@/api/generated/schema'
import SignatureStateBadge from './SignatureStateBadge.vue'
import {SignatureDisplay, signatureDisplayOf, waitsForNobody} from './signatureState'

function summary(overrides: Partial<SignatureSummary> = {}): SignatureSummary {
    return {
        requestUid: '7b0d3e0c-9f4e-4f0e-8a51-0c9d6c8f2a11',
        state: RequestState.OPEN,
        signed: 0,
        expected: 2,
        open: 2,
        nobodyCanSign: 0,
        ...overrides,
    }
}

/** How the signatures on a document read in the lists, from the summary of its newest request. */
describe('signatureDisplayOf', () => {
    it('reads each state of a request', () => {
        expect(signatureDisplayOf(null)).toBe(SignatureDisplay.NOT_ASKED)
        expect(signatureDisplayOf(summary())).toBe(SignatureDisplay.OPEN)
        expect(signatureDisplayOf(summary({signed: 1, open: 1}))).toBe(SignatureDisplay.PARTLY_SIGNED)
        expect(signatureDisplayOf(summary({state: RequestState.COMPLETE, signed: 2, open: 0})))
            .toBe(SignatureDisplay.SIGNED)
        expect(signatureDisplayOf(summary({state: RequestState.WITHDRAWN}))).toBe(SignatureDisplay.WITHDRAWN)
        expect(signatureDisplayOf(summary({state: RequestState.SUPERSEDED}))).toBe(SignatureDisplay.REPLACED)
    })

    it('says a field nobody can sign only while the request waits', () => {
        expect(waitsForNobody(summary({nobodyCanSign: 1}))).toBe(true)
        expect(waitsForNobody(summary())).toBe(false)
        expect(waitsForNobody(summary({state: RequestState.WITHDRAWN, nobodyCanSign: 1}))).toBe(false)
        expect(waitsForNobody(null)).toBe(false)
    })
})

describe('SignatureStateBadge', () => {
    it('counts the signed fields while the request waits and names a field nobody can sign', () => {
        const badge = mount(SignatureStateBadge, {props: {summary: summary({signed: 1, expected: 3, nobodyCanSign: 1})}})

        expect(badge.text()).toContain('Teilweise unterschrieben · 1 von 3')
        expect(badge.get('[data-testid="signature-nobody"]').text()).toBe('Niemand kann unterschreiben')
    })

    it('reads a complete request as signed without counting', () => {
        const badge = mount(SignatureStateBadge, {
            props: {summary: summary({state: RequestState.COMPLETE, signed: 2, open: 0})},
        })

        expect(badge.text()).toContain('Unterschrieben')
        expect(badge.text()).not.toContain('von')
        expect(badge.find('[data-testid="signature-nobody"]').exists()).toBe(false)
    })
})
