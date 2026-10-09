/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {OpenSignatureResponse} from '@/api/generated/schema'
import {chosenGroups, flowSteps, groupByDocument, namedFields, preselected} from './batchFlow'
import {picturesOf} from './useBatchSigning'

function field(fieldId: number, requestUid: string, overrides: Partial<OpenSignatureResponse> = {}): OpenSignatureResponse {
    return {
        fieldId,
        requestUid,
        documentId: 1,
        documentTitle: requestUid,
        memberName: 'Ben',
        fieldName: 'guardian1',
        role: 'GUARDIAN',
        capacity: 'GUARDIAN',
        memberId: 21,
        signerName: 'Jana',
        statement: 'Ja.',
        ...overrides,
    }
}

const FIELDS = [
    field(1, 'camp'),
    field(2, 'photos'),
    field(3, 'camp', {capacity: 'MEMBER_THROUGH_ACCOUNT', role: 'PARTICIPANT', signerName: 'Ben'}),
]

/** The flow's pure parts: grouping, preselection, the screens a selection leads through, and the pictures sent. */
describe('batchFlow', () => {
    it('groups by document and puts the document of the field it was opened for first', () => {
        expect(groupByDocument(FIELDS, '?').map(group => group.title)).toEqual(['camp', 'photos'])
        expect(groupByDocument(FIELDS, '?', 2).map(group => group.title)).toEqual(['photos', 'camp'])
        expect(groupByDocument(FIELDS, '?')[0]?.fields.map(each => each.fieldId)).toEqual([1, 3])
    })

    it('ticks everything unless the address names fields it holds', () => {
        expect(preselected(FIELDS, [])).toEqual([1, 2, 3])
        expect(preselected(FIELDS, [2, 99])).toEqual([2])
        expect(preselected(FIELDS, [99])).toEqual([1, 2, 3])
        expect(namedFields('2,x,3')).toEqual([2, 3])
        expect(namedFields(undefined)).toEqual([])
    })

    it('leads through one screen per document, the pictures, the check and the confirmation', () => {
        const groups = chosenGroups(groupByDocument(FIELDS, '?'), [1, 3])
        expect(flowSteps(groups).map(step => step.kind))
            .toEqual(['overview', 'document', 'holderPicture', 'memberPicture', 'check', 'confirm'])
        const childOnly = chosenGroups(groupByDocument(FIELDS, '?'), [3])
        expect(flowSteps(childOnly).map(step => step.kind)).toEqual(['overview', 'document', 'memberPicture', 'check', 'confirm'])
    })

    it('sends the reader\'s picture only when one was made, and one per member drawing their own', () => {
        const draft = {dataUrl: 'data:image/png;base64,QQ', source: 'TYPED' as const}
        expect(picturesOf({holder: {draft: null, useSaved: true, keep: false}, members: {21: draft}}))
            .toEqual([{memberId: 21, signatureImage: 'QQ', signatureSource: 'TYPED'}])
        expect(picturesOf({holder: {draft, useSaved: false, keep: true}, members: {}}))
            .toEqual([{signatureImage: 'QQ', signatureSource: 'TYPED', keepSignature: true}])
    })
})
