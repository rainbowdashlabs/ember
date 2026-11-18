/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {RegistrationStatus, type RegistrationResponse} from '@/api/generated/schema'
import StatusSection from './StatusSection.vue'

function declined(id: number, name: string): RegistrationResponse {
    return {
        id,
        eventId: 3,
        memberId: id,
        memberName: name,
        memberIdentity: null,
        eventDate: '2026-12-04',
        status: RegistrationStatus.DECLINED,
        createdAt: '2026-11-01T10:00:00Z',
        createdByName: null,
        fields: [],
        eventName: null,
        answersMissing: false,
        fromField: false,
        fieldName: null,
        agreementWithdrawnAt: null,
        placeGivenUp: false,
        queuePosition: null,
        statusChangedAt: '2026-11-10T18:00:00Z',
    }
}

/** Declined answers need no decision, so their group waits closed and shows little once opened. */
describe('StatusSection', () => {
    it('keeps the declined answers closed until their heading is pressed', async () => {
        const section = await mountSuspended(StatusSection, {
            props: {icon: 'user-slash', title: 'Abgesagt', registrations: [declined(1, 'Anna')], stats: [], declined: true},
        })

        expect(section.text()).toContain('Abgesagt (1)')
        expect(section.find('[data-testid="registration-stats-table"]').exists()).toBe(false)

        await section.get('[data-testid="status-section-toggle"]').trigger('click')

        expect(section.find('[data-testid="registration-stats-table"]').exists()).toBe(true)
        expect(section.text()).toContain('Abgesagt am')
        expect(section.text()).not.toContain('Angemeldet am')
    })

    it('shows any other group at once', async () => {
        const section = await mountSuspended(StatusSection, {
            props: {icon: 'check', title: 'Bestätigt', registrations: [declined(2, 'Ben')], stats: []},
        })

        expect(section.find('[data-testid="registration-stats-table"]').exists()).toBe(true)
        expect(section.find('[data-testid="status-section-toggle"]').exists()).toBe(false)
    })
})
