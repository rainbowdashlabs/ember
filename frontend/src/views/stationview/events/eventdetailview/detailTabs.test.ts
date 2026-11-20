/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {detailTabs, type DetailTabFacts} from './detailTabs'

const t = (key: string) => key

const FACTS: DetailTabFacts = {
    requiresRegistration: true,
    canManageAttendance: true,
    hasAttendanceTemplate: true,
    hasDate: true,
    attendanceSessionId: null,
    cancelled: false,
    canReadEquipment: false,
}

function keysOf(facts: Partial<DetailTabFacts>) {
    return detailTabs({...FACTS, ...facts}, t).map(tab => tab.key)
}

/** Which tabs an appointment's page offers, and what its second tab is called. */
describe('detailTabs', () => {
    it('names the second tab after the sign-ups, or after the sign-offs where nobody signs up', () => {
        expect(detailTabs(FACTS, t)[1]!.label).toBe('eventDetail.tabRegistrations')
        expect(detailTabs({...FACTS, requiresRegistration: false}, t)[1]!.label).toBe('eventDetail.tabSignOffs')
    })

    it('offers attendance in a tab of its own to whoever keeps it, where a list can be started', () => {
        expect(keysOf({})).toEqual(['info', 'registrations', 'attendance'])
    })

    it('offers no attendance tab to somebody who keeps no attendance', () => {
        expect(keysOf({canManageAttendance: false, attendanceSessionId: 3})).not.toContain('attendance')
    })

    it('starts no attendance list on a cancelled date but keeps one written before', () => {
        expect(keysOf({cancelled: true})).not.toContain('attendance')
        expect(keysOf({cancelled: true, attendanceSessionId: 3})).toContain('attendance')
    })

    it('offers the attendance tab where a list is kept even though the appointment names no template', () => {
        expect(keysOf({hasAttendanceTemplate: false, attendanceSessionId: 3})).toContain('attendance')
        expect(keysOf({hasAttendanceTemplate: false})).not.toContain('attendance')
    })

    it('puts the equipment last where it can be read', () => {
        expect(keysOf({canReadEquipment: true})).toEqual(['info', 'registrations', 'attendance', 'equipment'])
    })
})
