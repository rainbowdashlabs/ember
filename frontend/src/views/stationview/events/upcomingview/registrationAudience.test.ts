/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {registrationAudienceNote} from './registrationAudience'
import type {AllEventRestrictions, RestrictionAudience} from '@/api/events'
import type {MemberGroup, UserTag} from '@/api/types'

const t = (key: string, arg?: number | Record<string, unknown>) => {
    switch (key) {
        case 'eventsUpcoming.registrationAudience':
            return `Anmeldung nur für: ${(arg as Record<string, unknown>).audience}`
        case 'eventsUpcoming.audienceMemberOne':
            return 'ein einzeln ausgewähltes Mitglied'
        case 'eventsUpcoming.audienceMemberMany':
            return `${(arg as Record<string, unknown>).n} einzeln ausgewählte Mitglieder`
        case 'eventsUpcoming.audienceAnd':
            return ' und '
        case 'eventsUpcoming.audienceOr':
            return ' oder '
        default:
            return key
    }
}

const groups: MemberGroup[] = [{id: 1, stationId: 's', name: 'Jugend'}, {id: 2, stationId: 's', name: 'Aktive'}]
const tags: UserTag[] = [{id: 5, stationId: 's', name: 'Bootsführer'}]

function audience(partial: Partial<RestrictionAudience>): RestrictionAudience {
    return {userTypes: [], groupIds: [], tagIds: [], memberIds: [], ...partial}
}

function restrictions(register: Partial<RestrictionAudience>, view: Partial<RestrictionAudience> = {}): AllEventRestrictions {
    return {7: {register: audience(register), view: audience(view)}}
}

describe('registrationAudienceNote', () => {
    it('says nothing for an event without restrictions', () => {
        expect(registrationAudienceNote({}, 7, groups, tags, t)).toBeNull()
    })

    it('says nothing when the answering audience is unrestricted', () => {
        expect(registrationAudienceNote(restrictions({}, {groupIds: [1]}), 7, groups, tags, t)).toBeNull()
    })

    it('says nothing when both audiences are the same', () => {
        const r = restrictions({groupIds: [1, 2]}, {groupIds: [2, 1]})
        expect(registrationAudienceNote(r, 7, groups, tags, t)).toBeNull()
    })

    it('names groups and joins values of one kind with oder', () => {
        const r = restrictions({groupIds: [1, 2]})
        expect(registrationAudienceNote(r, 7, groups, tags, t))
            .toBe('Anmeldung nur für: Jugend oder Aktive')
    })

    it('joins kinds by the audience mode', () => {
        const und = restrictions({userTypes: ['TEAM'], tagIds: [5], mode: 'AND'})
        expect(registrationAudienceNote(und, 7, groups, tags, t))
            .toBe('Anmeldung nur für: Team und Bootsführer')
        const oder = restrictions({userTypes: ['TEAM'], tagIds: [5], mode: 'OR'})
        expect(registrationAudienceNote(oder, 7, groups, tags, t))
            .toBe('Anmeldung nur für: Team oder Bootsführer')
    })

    it('counts individually picked members instead of naming them', () => {
        const r = restrictions({groupIds: [1], memberIds: [10, 11], mode: 'AND'})
        expect(registrationAudienceNote(r, 7, groups, tags, t))
            .toBe('Anmeldung nur für: Jugend oder 2 einzeln ausgewählte Mitglieder')
    })

    it('falls back to the id for a group it cannot name', () => {
        const r = restrictions({groupIds: [99]})
        expect(registrationAudienceNote(r, 7, groups, tags, t))
            .toBe('Anmeldung nur für: #99')
    })
})
