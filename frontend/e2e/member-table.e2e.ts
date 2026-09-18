/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, type Page} from './fixtures/auth'

/**
 * A table of people with the columns a station chose, on an appointment and on the register.
 *
 * <p>The stories are about who sees what rather than about the drawing. A question the station asks
 * only of its team belongs on the sheet for whoever keeps the register and must be absent, not blank,
 * for everybody else, because a column of blanks about named people says that something was withheld
 * about those people in particular.
 */

/** A question of the station's, asked of one audience and answered by one member. */
async function askEveryone(api: ReturnType<Page['request']>, headers: Record<string, string>, name: string) {
    const created = await api.post('/api/v1/profile-fields', {
        headers,
        data: {name, fieldType: 'TEXT', config: {}, required: false, readonly: false},
    })
    expect(created.ok(), `the station asked "${name}" (${await created.text()})`).toBeTruthy()
    return (await created.json()).id as number
}

test.describe('A table of people', () => {
    /**
     * Whoever handles the registrations gets the table, and the questions their own permissions reach.
     *
     * <p>The picker and the sheet are filled from the same place on purpose: a picker offering a
     * column the table would then drop teaches a station to distrust the sheet it carries.
     */
    test('whoever handles the registrations finds the table', async ({managerPage}) => {
        const headers = await apiHeaders(managerPage)
        const stamp = `${test.info().workerIndex}-${Date.now()}`
        const name = `Schuhgröße ${stamp}`
        await askEveryone(managerPage.request, headers, name)

        const created = await managerPage.request.post('/api/v1/events', {
            headers,
            data: {
                name: `Tabellentermin ${stamp}`,
                description: 'Wer kommt mit',
                eventType: 'ONE_TIME',
                startTime: new Date(Date.now() + 9 * 86400000).toISOString(),
                endTime: new Date(Date.now() + 9 * 86400000 + 3600000).toISOString(),
                requiresRegistration: true,
            },
        })
        expect(created.ok(), await created.text()).toBeTruthy()
        const eventId = (await created.json()).id

        try {
            await managerPage.goto(`/station/events/${eventId}`)
            await managerPage.getByRole('button', {name: 'Anmeldungen'}).click()

            await managerPage.getByTestId('open-registration-table').click()
            const picker = managerPage.getByTestId('member-table-preview')
            await expect(picker, 'the table opens for whoever handles the registrations').toBeVisible()

            await managerPage.getByRole('checkbox', {name}).check()
            await picker.click()
            await expect(
                managerPage.getByTestId('member-table-preview-table'),
                'and draws with the question that was ticked',
            ).toContainText(name)
        } finally {
            await managerPage.request.delete(`/api/v1/events/${eventId}`, {headers})
        }
    })

    /**
     * Somebody who may see the appointment but not its registrations is not offered it at all.
     *
     * <p>Refused rather than thinned: a member's number on the register, their age and where they live
     * are confidential whichever columns anybody picks, so the permission comes before the scopes.
     */
    test('a member who may only see the appointment is not offered it', async ({managerPage, memberPage}) => {
        const headers = await apiHeaders(managerPage)
        const stamp = `${test.info().workerIndex}-${Date.now()}`

        const created = await managerPage.request.post('/api/v1/events', {
            headers,
            data: {
                name: `Ohne Tabelle ${stamp}`,
                description: 'Nur gucken',
                eventType: 'ONE_TIME',
                startTime: new Date(Date.now() + 10 * 86400000).toISOString(),
                endTime: new Date(Date.now() + 10 * 86400000 + 3600000).toISOString(),
                requiresRegistration: true,
            },
        })
        expect(created.ok(), await created.text()).toBeTruthy()
        const eventId = (await created.json()).id

        try {
            await memberPage.goto(`/station/events/${eventId}`)
            await memberPage.getByRole('button', {name: 'Anmeldungen'}).click()
            await expect(
                memberPage.getByTestId('open-registration-table'),
                'the table is not there to be pressed',
            ).toHaveCount(0)

            const refused = await memberPage.request.post(`/api/v1/events/${eventId}/registration-table`, {
                headers: await apiHeaders(memberPage),
                data: {date: new Date(Date.now() + 10 * 86400000).toISOString().slice(0, 10), columns: []},
            })
            expect(refused.ok(), 'and asking for it outright is refused rather than answered thinly').toBeFalsy()
        } finally {
            await managerPage.request.delete(`/api/v1/events/${eventId}`, {headers})
        }
    })

    /** The register offers the same table to whoever may carry the list out. */
    test('the register offers the same table', async ({managerPage}) => {
        await managerPage.goto('/station/members/list')
        await managerPage.getByTestId('open-member-table').click()

        await expect(
            managerPage.getByTestId('member-table-preview'),
            'one table, reached from the other screen',
        ).toBeVisible()

        await managerPage.getByRole('checkbox', {name: 'Name'}).check()
        await managerPage.getByTestId('member-table-preview').click()
        await expect(
            managerPage.getByTestId('member-table-preview-table'),
            'and it draws the people the screen was showing',
        ).toBeVisible()
    })
})
