/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {FieldType, SelfRegistration, type AppointmentField} from '@/api/generated/schema'
import EventMetaGrid from './EventMetaGrid.vue'

vi.mock('@/api', () => ({
    events: {toggleFieldSelfRegistration: vi.fn()},
}))

const ME = 5

function memberQuestion(over: Partial<AppointmentField>): AppointmentField {
    return {
        id: 1,
        eventId: 7,
        name: 'Fahrer',
        fieldType: FieldType.MEMBER_LIST,
        value: '',
        config: {perDate: false, selfRegistration: true, required: false, managersOnly: false},
        position: 0,
        overview: false,
        isPublic: false,
        attendanceFieldId: null,
        selfRegistrationState: SelfRegistration.OPEN,
        ...over,
    }
}

async function buttonsFor(field: AppointmentField): Promise<string[]> {
    const grid = await mountSuspended(EventMetaGrid, {
        props: {eventId: 7, fields: [field], allMembers: [], currentMemberId: ME, templateName: '', canEditEvent: false},
    })
    return grid.findAll('button').map(button => button.text())
}

/**
 * The self-registration buttons follow what the server says about this reader, so a member is never
 * offered a question they would then be refused.
 */
describe('EventMetaGrid self-registration', () => {
    it('offers stepping in where the question is open', async () => {
        expect(await buttonsFor(memberQuestion({}))).toEqual(['Eintragen'])
    })

    it('offers nothing where the question is narrowed to others', async () => {
        expect(await buttonsFor(memberQuestion({selfRegistrationState: SelfRegistration.NOT_ELIGIBLE}))).toEqual([])
    })

    it('offers nothing for a question that came without a state', async () => {
        expect(await buttonsFor(memberQuestion({selfRegistrationState: null}))).toEqual([])
    })

    it('says closed once the appointment stopped taking people', async () => {
        expect(await buttonsFor(memberQuestion({selfRegistrationState: SelfRegistration.CLOSED})))
            .toEqual(['Eintragung geschlossen'])
    })

    it('always lets somebody standing in the question step out', async () => {
        const standingIn = memberQuestion({value: String(ME), selfRegistrationState: SelfRegistration.NOT_ELIGIBLE})
        expect(await buttonsFor(standingIn)).toEqual(['Austragen'])
    })

    it('shows a taken single slot as taken', async () => {
        expect(await buttonsFor(memberQuestion({fieldType: FieldType.MEMBER, value: '9'}))).toEqual(['Belegt'])
    })
})
