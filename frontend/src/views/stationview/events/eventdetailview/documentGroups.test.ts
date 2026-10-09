/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {PaperState, PartnerAgreementState, RequirementSignatureState} from '@/api/generated/schema'
import {asked, consentCopy, lena, partnerSigner, scan} from '@/test/mocks/documentsToBring'
import {DocumentGroup, copyGroup, groupEntries, partnerGroup} from './documentGroups'

const signed = asked(RequirementSignatureState.SIGNED, RequirementSignatureState.SIGNED)
const open = asked(RequirementSignatureState.OPEN, RequirementSignatureState.OPEN)

/**
 * Every person on a manager's view of a document lands in one group: what needs the manager, what only
 * needs waiting for, and what is done.
 */
describe('documentGroups', () => {
    it('puts what needs the manager into the first group', () => {
        expect(copyGroup(consentCopy({paper: scan(PaperState.SUBMITTED)}), null)).toBe(DocumentGroup.TODO)
        expect(copyGroup(consentCopy({signature: {...signed, state: RequirementSignatureState.REVOKED}}), null))
            .toBe(DocumentGroup.TODO)
        const nobody = {...open, fields: [{...open.fields[1]!, nobodyCanSign: true}]}
        expect(copyGroup(consentCopy({signature: nobody}), null)).toBe(DocumentGroup.TODO)
        expect(copyGroup(consentCopy({signature: open}), '2026-10-07T12:00:00Z')).toBe(DocumentGroup.TODO)
    })

    it('leaves what the participant still owes to waiting', () => {
        expect(copyGroup(consentCopy(), null)).toBe(DocumentGroup.MISSING)
        expect(copyGroup(consentCopy({signature: open}), null)).toBe(DocumentGroup.MISSING)
        expect(copyGroup(consentCopy({paper: scan(PaperState.REJECTED, 'Unscharf')}), null)).toBe(DocumentGroup.MISSING)
    })

    it('counts signed, paper confirmed and waived copies as done, a withdrawn agreement signed anew too', () => {
        expect(copyGroup(consentCopy({signature: signed}), null)).toBe(DocumentGroup.DONE)
        expect(copyGroup(consentCopy({paper: scan(PaperState.CONFIRMED)}), null)).toBe(DocumentGroup.DONE)
        expect(copyGroup(consentCopy({signature: {...signed, state: RequirementSignatureState.WAIVED}}), null))
            .toBe(DocumentGroup.DONE)
        expect(copyGroup(consentCopy({signature: signed}), '2026-10-07T12:00:00Z')).toBe(DocumentGroup.DONE)
    })

    it('sorts the members of partner stations by where they stand', () => {
        const groupOf = (state: PartnerAgreementState) => partnerGroup(partnerSigner(state).documents[0]!)
        expect(groupOf(PartnerAgreementState.MISSING)).toBe(DocumentGroup.TODO)
        expect(groupOf(PartnerAgreementState.WITHDRAWN)).toBe(DocumentGroup.TODO)
        expect(groupOf(PartnerAgreementState.ASKED)).toBe(DocumentGroup.MISSING)
        expect(groupOf(PartnerAgreementState.SIGNED)).toBe(DocumentGroup.DONE)
        expect(groupOf(PartnerAgreementState.PAPER_CONFIRMED)).toBe(DocumentGroup.DONE)
        const partly = {...partnerSigner(PartnerAgreementState.SIGNED).documents[0]!, complete: false}
        expect(partnerGroup(partly)).toBe(DocumentGroup.MISSING)
    })

    it('groups the station\'s participants first and the partners\' members after them, of one document only', () => {
        const tim = {...lena(null, signed), memberId: 12, name: 'Tim Schmidt'}
        const grouped = groupEntries(
            [lena(scan(PaperState.SUBMITTED)), tim],
            [partnerSigner(PartnerAgreementState.MISSING), {...partnerSigner(PartnerAgreementState.MISSING), documents: []}],
            8)

        expect(grouped.todo.map(entry => entry.kind)).toEqual(['participant', 'partner'])
        expect(grouped.missing).toHaveLength(0)
        expect(grouped.done).toHaveLength(1)
        expect(groupEntries([lena()], [], 9)).toEqual({todo: [], missing: [], done: []})
    })
})
