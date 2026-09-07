/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * A checklist without a single column has nothing to tick, and the dialog refuses to save one, so
 * the story fills a column too.
 */
test.describe('Checklists', () => {
    /**
     * A checklist is a grid of people against things to tick, and ticking is the whole of using
     * one. The mark has to survive a reload, which is what says it reached the server.
     */
    test('a tick on a checklist is kept', async ({managerPage: page}) => {
        await page.goto('/station/checklist')

        await page.locator('main').getByText(/./).first().waitFor()
        await page.getByRole('link').filter({hasText: /./}).first().waitFor().catch(() => undefined)

        const entry = page.locator('main [class*="cursor-pointer"]').first()
        await entry.click()
        await page.waitForURL(/\/station\/checklist\/\d+/)

        const cell = page.getByRole('switch').first()
        await expect(cell).toBeVisible()
        const before = await cell.getAttribute('aria-checked')

        await cell.click()
        await page.reload()

        await expect(page.getByRole('switch').first()).not.toHaveAttribute('aria-checked', before ?? 'false')
    })

    test('a checklist is created', async ({managerPage: page}) => {
        const checklist = unique('Checkliste')

        await page.goto('/station/checklist')
        await page.getByRole('button', {name: 'Neue Checkliste'}).click()

        const fields = page.getByRole('textbox')
        await fields.first().fill(checklist)
        await fields.nth(2).fill('Erledigt')

        await page.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(checklist)).toBeVisible()
    })

    /**
     * A list tied to an evening picks up whoever signs up afterwards, once somebody presses refresh.
     *
     * <p>That last clause is the whole story. "Follows" invites the reading that the list keeps
     * itself up to date, and it does not: the late sign-up sits there unseen until the button is
     * pressed, and only then does the name arrive. So the story signs somebody up between two reads
     * of the same grid and asserts that they are absent from the first and present in the second.
     */
    test('a list that follows an appointment picks up a late sign-up on refresh', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const appointment = unique('Folgetermin')
        const checklist = unique('Folgeliste')
        const start = new Date(Date.now() + 5 * 86400000)

        const created = await page.request.post('/api/v1/events', {
            headers,
            data: {
                name: appointment,
                description: 'Liste folgt diesem Abend',
                eventType: 'ONE_TIME',
                startTime: start.toISOString(),
                endTime: new Date(start.getTime() + 3600000).toISOString(),
                requiresRegistration: true,
            },
        })
        expect(created.ok(), `the organiser made an appointment (${await created.text()})`).toBeTruthy()
        const eventId = (await created.json()).id

        const session = await page.request.get('/api/v1/session', {headers})
        const own = (await session.json())?.member?.id
        const completions = await page.request.get('/api/v1/station-members/completions', {headers})
        const people: {id: number; name: string}[] = (await completions.json())
            .filter((entry: {id: number}) => entry.id !== own)
            .slice(0, 2)
        expect(people.length, 'the seeded station has two members to sign up').toBe(2)

        const early = await page.request.post(`/api/v1/events/${eventId}/register`,
            {headers, data: {memberId: people[0]!.id}})
        expect(early.ok(), `the first place was taken (${await early.text()})`).toBeTruthy()

        await page.goto('/station/checklist')
        await page.getByRole('button', {name: 'Neue Checkliste'}).click()

        const fields = page.getByRole('textbox')
        await fields.first().fill(checklist)
        await fields.nth(2).fill('Zettel abgegeben')

        await page.getByTestId('checklist-follows-event').check()
        const picker = page.getByTestId('checklist-occurrence-picker').locator('input[type="search"]')
        await picker.click()
        await picker.fill(appointment)
        await page.getByText(appointment).last().click()

        await page.getByTestId('modal').getByRole('button', {name: 'Speichern'}).click()
        await page.waitForURL(/\/station\/checklist\/\d+/)

        await expect(page.getByTestId('checklist-follows'), 'the header says what it follows').toBeVisible()
        await expect(page.getByText(people[0]!.name).first(), 'the place taken before the list was made')
            .toBeVisible()
        await expect(page.getByText(people[1]!.name), 'nobody who has not signed up yet').toHaveCount(0)

        const late = await page.request.post(`/api/v1/events/${eventId}/register`,
            {headers, data: {memberId: people[1]!.id}})
        expect(late.ok(), `a second place was taken afterwards (${await late.text()})`).toBeTruthy()

        await page.reload()
        await expect(page.getByText(people[1]!.name), 'and reading the list alone brings nobody in')
            .toHaveCount(0)

        await page.getByRole('button', {name: 'Auffrischen'}).click()
        await expect(page.getByText(people[1]!.name).first(), 'the late sign-up arrives with the refresh')
            .toBeVisible()

        await page.request.delete(`/api/v1/events/${eventId}`, {headers})
    })
})
