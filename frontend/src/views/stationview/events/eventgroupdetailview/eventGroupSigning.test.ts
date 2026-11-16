/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {RequirementSignatureState, type AppointmentDocuments, type EventGroupPartState} from '@/api/generated/schema'
import {eventGroupSigningStep} from './eventGroupSigning'

const documentsToBring = vi.hoisted(() => vi.fn())

vi.mock('@/api', () => ({appointmentDocuments: {documentsToBring}}))

function part(eventId: number, startTime: string, calledOff = false): EventGroupPartState {
    return {eventId, setId: 1, name: `Tag ${eventId}`, startTime, endTime: startTime, calledOff, tookPlace: false, required: false}
}

function documents(open: {templateId: number; fromEventGroup: boolean}[], memberId = 12): AppointmentDocuments {
    return {
        required: open.map(({templateId, fromEventGroup}) => ({
            templateId, name: `Dokument ${templateId}`, kind: 'LETTER', version: 1, archived: false, lastUsedAt: null, fromEventGroup,
        })),
        own: [{
            memberId,
            name: 'Kim Gerber',
            agreementWithdrawnAt: null,
            documents: open.map(({templateId}) => ({
                templateId, name: `Dokument ${templateId}`, status: 'GENERATED', documentId: 1, generatedAt: null,
                outdated: false, paper: null, agreementOffered: false,
                signature: {state: RequirementSignatureState.OPEN},
            })),
        }],
        participants: null,
    } as unknown as AppointmentDocuments
}

/** The step offers the event group's documents once, on the first chosen part that shows them waiting. */
describe('eventGroupSigningStep', () => {
    beforeEach(() => documentsToBring.mockReset())

    it('takes the first chosen part in date order and only the event group\'s documents', async () => {
        documentsToBring.mockResolvedValue(documents([{templateId: 8, fromEventGroup: true}, {templateId: 9, fromEventGroup: false}]))

        const step = await eventGroupSigningStep(
            [part(21, '2027-03-08T08:00:00Z'), part(20, '2027-03-07T08:00:00Z')], [20, 21], 12, 'Europe/Berlin')

        expect(documentsToBring).toHaveBeenCalledTimes(1)
        expect(documentsToBring).toHaveBeenCalledWith(20, '2027-03-07')
        expect(step?.eventId).toBe(20)
        expect(step?.copies.map(copy => copy.document.templateId)).toEqual([8])
    })

    it('offers nothing where only a part\'s own documents wait, or where they are another person\'s', async () => {
        documentsToBring.mockResolvedValue(documents([{templateId: 9, fromEventGroup: false}]))
        expect(await eventGroupSigningStep([part(20, '2027-03-07T08:00:00Z')], [20], 12, 'Europe/Berlin')).toBeNull()

        documentsToBring.mockResolvedValue(documents([{templateId: 8, fromEventGroup: true}], 13))
        expect(await eventGroupSigningStep([part(20, '2027-03-07T08:00:00Z')], [20], 12, 'Europe/Berlin')).toBeNull()
    })

    it('leaves out parts not chosen or called off', async () => {
        documentsToBring.mockResolvedValue(documents([{templateId: 8, fromEventGroup: true}]))

        const step = await eventGroupSigningStep(
            [part(20, '2027-03-07T08:00:00Z', true), part(21, '2027-03-08T08:00:00Z'), part(22, '2027-03-09T08:00:00Z')],
            [20, 22], 12, 'Europe/Berlin')

        expect(documentsToBring).toHaveBeenCalledWith(22, '2027-03-09')
        expect(step?.eventId).toBe(22)
    })
})
