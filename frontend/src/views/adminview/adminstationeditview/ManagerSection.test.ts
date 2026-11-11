/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {ReminderTrigger, StationState, type AdminStationSummary} from '@/api/generated/schema'
import ManagerSection from './ManagerSection.vue'

vi.mock('@/api/stations', () => ({remindManager: vi.fn()}))

function summary(state: StationState): AdminStationSummary {
    return {
        id: '00000000-0000-0000-0000-000000000007',
        name: 'Nord',
        state,
        memberCount: 1,
        managerName: 'Mara Nager',
        managerEmail: 'mara@test.example',
        waitingSince: '2026-11-01T10:00:00Z',
        createdAt: null,
        associationName: null,
        lastReminder: {sentAt: '2026-11-08T09:00:00Z', trigger: ReminderTrigger.AFTER_7_DAYS},
        movedAway: false,
    }
}

const MANAGER = {email: 'mara@test.example', firstName: 'Mara', lastName: 'Nager', accountReady: false}

/** The manager of a station on its page, with the reminder next to a setup that is still pending. */
describe('ManagerSection', () => {
    function section(state: StationState | null) {
        return mount(ManagerSection, {
            props: {
                manager: MANAGER,
                summary: state ? summary(state) : null,
                editingManager: false,
                isEdit: true,
                managerEmail: '',
            },
        })
    }

    it('offers the reminder next to the pending setup and says when the last one went out', () => {
        const view = section(StationState.WAITING_FOR_MANAGER)

        expect(view.text()).toContain('Einrichtung ausstehend')
        expect(view.text()).toContain('Wartet auf Verwaltung')
        expect(view.find('[data-testid="remind-manager"]').exists()).toBe(true)
        expect(view.find('[data-testid="last-reminder"]').text()).toBe('Zuletzt automatisch erinnert am 08.11.2026')
    })

    it('names a station being set up as such next to the reminder', () => {
        const view = section(StationState.SETTING_UP)

        expect(view.text()).toContain('In Einrichtung')
        expect(view.find('[data-testid="remind-manager"]').exists()).toBe(true)
    })

    it('offers no reminder for a station that is set up, nor before the station is known', () => {
        expect(section(StationState.SET_UP).find('[data-testid="remind-manager"]').exists()).toBe(false)
        expect(section(null).find('[data-testid="remind-manager"]').exists()).toBe(false)
    })
})
