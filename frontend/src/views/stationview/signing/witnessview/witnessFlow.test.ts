/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import type {WitnessFieldResponse} from '@/api/generated/schema'
import {afterChoosing, handedOver, peopleMatching, personOf, positionOf} from './witnessFlow'

const ANNA = {memberId: 1, name: 'Anna Probe', userType: 'TRIAL' as const}
const BERT = {memberId: 2, name: 'Bert Muster', userType: 'MEMBER' as const}

function field(signers: WitnessFieldResponse['signers'], memberId: number | null = 1): WitnessFieldResponse {
    return {
        fieldId: 9,
        requestUid: '11111111-1111-1111-1111-111111111111',
        documentId: 3,
        documentTitle: 'Fotoerlaubnis',
        memberId,
        memberName: 'Anna Probe',
        fieldName: 'participant',
        role: 'PARTICIPANT',
        statement: 'Ich erlaube Fotos.',
        signers,
    }
}

describe('witnessFlow', () => {
    it('hands the device over only for the screens the person holds it for', () => {
        expect(['handOver', 'read', 'draw', 'takeBack'].every(step => handedOver(step as never))).toBe(true)
        expect(['person', 'document', 'signer', 'confirm'].some(step => handedOver(step as never))).toBe(false)
        expect(positionOf('person')).toBe(1)
        expect(positionOf('confirm')).toBe(8)
    })

    it('leads a chosen field to the hand over, to who is there, or nowhere', () => {
        expect(afterChoosing(field([ANNA]))).toEqual({step: 'handOver', signer: ANNA})
        expect(afterChoosing(field([ANNA, BERT]))).toEqual({step: 'signer', signer: null})
        expect(afterChoosing(field([]))).toBeNull()
    })

    it('names the person a document is about, and nobody once they are gone', () => {
        expect(personOf(field([ANNA]))).toEqual(ANNA)
        expect(personOf(field([BERT]))?.name).toBe('Anna Probe')
        expect(personOf(field([], null))).toBeNull()
    })

    it('finds people by any part of their name, ignoring case', () => {
        expect(peopleMatching([ANNA, BERT], 'probe')).toEqual([ANNA])
        expect(peopleMatching([ANNA, BERT], '  ')).toEqual([ANNA, BERT])
    })
})
