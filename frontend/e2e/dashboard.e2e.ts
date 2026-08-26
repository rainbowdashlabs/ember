/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'

test.describe('Dashboard', () => {
    test('the dashboard shows a member their day', async ({memberPage: page}) => {
        await page.goto('/station/dashboard/overview')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('link', {name: /Profil/}).first()).toBeVisible()
    })

    test('the station statistics render', async ({managerPage: page}) => {
        await page.goto('/station/dashboard/statistics')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText('Statistiken').first()).toBeVisible()
    })

    /**
     * An event whose registration is running out and which nobody has answered gets a section of its
     * own, so it is not found only by opening the calendar. One row per event, naming who still owes an
     * answer, because a household usually answers the same way for everyone.
     */
    test('an unanswered event whose registration is closing appears on the dashboard',
        async ({managerPage, memberPage}) => {
            const managerHeaders = await apiHeaders(managerPage)
            const name = `Antwort offen ${test.info().workerIndex}-${Date.now()}`

            const created = await managerPage.request.post('/api/v1/events', {
                headers: managerHeaders,
                data: {
                    name,
                    description: 'Bald zu',
                    eventType: 'ONE_TIME',
                    startTime: new Date(Date.now() + 9 * 86400000).toISOString(),
                    endTime: new Date(Date.now() + 9 * 86400000 + 3600000).toISOString(),
                    requiresRegistration: true,
                    registrationDeadline: new Date(Date.now() + 2 * 86400000).toISOString(),
                },
            })
            expect(created.ok(), `the organiser made an event (${await created.text()})`).toBeTruthy()
            const eventId = (await created.json()).id

            await memberPage.goto('/station/dashboard/overview')
            const row = memberPage.getByTestId('awaiting-answer').filter({hasText: name})
            await expect(row).toHaveCount(1, {timeout: 15000})

            // Answering makes the row go, which is what the section is for
            const memberHeaders = await apiHeaders(memberPage)
            const declined = await memberPage.request.post(`/api/v1/events/${eventId}/decline`,
                {headers: memberHeaders, data: {}})
            expect(declined.ok(), `the member answered (${await declined.text()})`).toBeTruthy()

            await memberPage.reload()
            await expect(memberPage.getByTestId('awaiting-answer').filter({hasText: name}))
                .toHaveCount(0, {timeout: 15000})

            await managerPage.request.delete(`/api/v1/events/${eventId}`, {headers: managerHeaders})
        })
})
