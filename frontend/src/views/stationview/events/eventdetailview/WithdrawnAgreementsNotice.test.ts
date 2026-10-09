/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {RegistrationStatus, type RegistrationResponse} from '@/api/generated/schema'
import WithdrawnAgreementsNotice from './WithdrawnAgreementsNotice.vue'

function registration(id: number, name: string, agreementWithdrawnAt: string | null): RegistrationResponse {
    return {
        id,
        eventId: 3,
        memberId: id,
        memberName: name,
        memberIdentity: null,
        eventDate: '2026-10-08',
        status: RegistrationStatus.ACCEPTED,
        createdAt: '2026-10-01T10:00:00Z',
        createdByName: null,
        eventName: null,
        answersMissing: false,
        fromField: false,
        fieldName: null,
        agreementWithdrawnAt,
        fields: [],
    }
}

/** Whoever runs the appointment reads whose signed agreement was withdrawn while they stayed registered. */
describe('WithdrawnAgreementsNotice', () => {
    it('names every registration whose agreement was withdrawn', async () => {
        const notice = await mountSuspended(WithdrawnAgreementsNotice, {
            props: {
                registrations: [
                    registration(1, 'Lena Schmidt', '2026-10-07T12:00:00Z'),
                    registration(2, 'Paul Weber', null),
                ],
            },
        })

        const text = notice.find('[data-testid="withdrawn-agreements"]').text()
        expect(text).toContain('Lena Schmidt')
        expect(text).not.toContain('Paul Weber')
    })

    it('says nothing where no agreement was withdrawn', async () => {
        const notice = await mountSuspended(WithdrawnAgreementsNotice, {
            props: {registrations: [registration(2, 'Paul Weber', null)]},
        })

        expect(notice.find('[data-testid="withdrawn-agreements"]').exists()).toBe(false)
    })
})
