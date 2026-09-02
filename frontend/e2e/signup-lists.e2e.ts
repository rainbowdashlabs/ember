/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, type Page} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * A list of names made out of who is coming.
 *
 * <p>The sign-ups are arranged through the endpoints rather than by clicking three members through
 * three browsers: what the story is about starts at the menu, and everything before it is setting a
 * table. What it then asserts is only what the reader sees, which is the grid the menu lands them on
 * and who stands in it.
 */

interface Person {
    id: number
    name: string
}

/** Three members of the station who are not the person running the story. */
async function threeOthers(page: Page, headers: Record<string, string>): Promise<Person[]> {
    const session = await page.request.get('/api/v1/session', {headers})
    expect(session.ok(), `the session is readable (${await session.text()})`).toBeTruthy()
    const own = (await session.json())?.member?.id

    const response = await page.request.get('/api/v1/station-members/completions', {headers})
    expect(response.ok(), `the station names its members (${await response.text()})`).toBeTruthy()
    const people: Person[] = (await response.json())
        .filter((entry: Person) => entry.id !== own)
        .slice(0, 3)
    expect(people.length, 'the seeded station has three members to sign up').toBe(3)
    return people
}

test.describe('Lists from sign-ups', () => {
    /**
     * Three sign up, two are confirmed, and the checklist made from the menu holds those two.
     *
     * <p>The third is the point of the story: a place that is still being thought about is not a
     * place, and a list built from "might come" is a list nobody can work from.
     */
    test('a checklist made from the sign-ups holds only the confirmed', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const appointment = unique('Anmeldeliste')
        const column = 'Ausrüstung erhalten'

        const created = await page.request.post('/api/v1/events', {
            headers,
            data: {
                name: appointment,
                description: 'Aus den Anmeldungen',
                eventType: 'ONE_TIME',
                startTime: new Date(Date.now() + 25 * 86400000).toISOString(),
                endTime: new Date(Date.now() + 25 * 86400000 + 3600000).toISOString(),
                requiresRegistration: true,
                requiresConfirmation: true,
            },
        })
        expect(created.ok(), `the organiser made an appointment (${await created.text()})`).toBeTruthy()
        const eventId = (await created.json()).id

        const people = await threeOthers(page, headers)
        const registrationIds: number[] = []
        for (const person of people) {
            const signedUp = await page.request.post(`/api/v1/events/${eventId}/register`,
                {headers, data: {memberId: person.id}})
            expect(signedUp.ok(), `${person.name} signed up (${await signedUp.text()})`).toBeTruthy()
            registrationIds.push((await signedUp.json()).id)
        }

        for (const registrationId of registrationIds.slice(0, 2)) {
            const confirmed = await page.request.put(
                `/api/v1/events/registrations/${registrationId}/status`,
                {headers, data: {status: 'ACCEPTED'}})
            expect(confirmed.ok(), `a place was confirmed (${await confirmed.text()})`).toBeTruthy()
        }

        await page.goto(`/station/events/${eventId}`)
        await page.getByRole('button', {name: 'Anmeldungen'}).click()

        await page.getByRole('button', {name: 'Aus den Anmeldungen'}).click()
        await page.getByTestId('signup-checklist-entry').click()

        await page.getByTestId('signup-checklist-column').fill(column)
        await page.getByTestId('modal').getByRole('button', {name: 'Speichern'}).click()

        await page.waitForURL(/\/station\/checklist\/\d+/)

        await expect(page.getByText(column).first(), 'the column it was given is there').toBeVisible()
        await expect(page.getByText(people[0]!.name).first(), 'the first confirmed place').toBeVisible()
        await expect(page.getByText(people[1]!.name).first(), 'the second confirmed place').toBeVisible()
        await expect(page.getByText(people[2]!.name), 'the place nobody has confirmed yet').toHaveCount(0)

        await expect(page.getByTestId('checklist-frozen'), 'and the list says it will not catch up')
            .toBeVisible()

        await page.request.delete(`/api/v1/events/${eventId}`, {headers})
    })

    /**
     * The same menu makes a survey, and the survey is honest about what it is not yet.
     *
     * <p>A form is written, then told who it is for, and only then published, so what the menu can
     * hand over is a draft addressed to the right people. The story lands in the editor and reads
     * back who the draft is addressed to, because that is the part no screen states outright.
     */
    test('a survey made from the sign-ups is a draft addressed to the confirmed', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const appointment = unique('Umfrageliste')

        const created = await page.request.post('/api/v1/events', {
            headers,
            data: {
                name: appointment,
                description: 'Aus den Anmeldungen',
                eventType: 'ONE_TIME',
                startTime: new Date(Date.now() + 26 * 86400000).toISOString(),
                endTime: new Date(Date.now() + 26 * 86400000 + 3600000).toISOString(),
                requiresRegistration: true,
            },
        })
        expect(created.ok(), `the organiser made an appointment (${await created.text()})`).toBeTruthy()
        const eventId = (await created.json()).id

        const people = (await threeOthers(page, headers)).slice(0, 2)
        for (const person of people) {
            const signedUp = await page.request.post(`/api/v1/events/${eventId}/register`,
                {headers, data: {memberId: person.id}})
            expect(signedUp.ok(), `${person.name} signed up (${await signedUp.text()})`).toBeTruthy()
        }

        await page.goto(`/station/events/${eventId}`)
        await page.getByRole('button', {name: 'Anmeldungen'}).click()

        await page.getByRole('button', {name: 'Aus den Anmeldungen'}).click()
        await page.getByTestId('signup-survey-entry').click()

        await expect(page.getByTestId('signup-survey-name'), 'the appointment names the survey')
            .toHaveValue(new RegExp(appointment))
        await page.getByTestId('modal').getByRole('button', {name: 'Erstellen'}).click()

        await page.waitForURL(/\/station\/forms\/\d+\/edit/)
        const formId = page.url().match(/forms\/(\d+)/)?.[1]

        await expect(page.getByRole('button', {name: 'Zurücksetzen'}).first(),
            'the draft is addressed to somebody rather than to everybody').toBeVisible()

        const restrictions = await page.request.get(`/api/v1/forms/${formId}/restrictions`, {headers})
        expect(restrictions.ok(), `the draft says who it is for (${await restrictions.text()})`).toBeTruthy()
        expect(([...(await restrictions.json()).memberIds ?? []] as number[]).sort(),
            'exactly the people who hold a place').toEqual(people.map(person => person.id).sort())

        const form = await page.request.get(`/api/v1/forms/${formId}`, {headers})
        expect((await form.json()).status, 'and it is still a draft').toBe('DRAFT')

        await page.request.delete(`/api/v1/forms/${formId}`, {headers})
        await page.request.delete(`/api/v1/events/${eventId}`, {headers})
    })
})
