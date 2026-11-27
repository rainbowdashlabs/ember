/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, type Page} from './fixtures/auth'
import {ownMember} from './fixtures/ownMember'
import {unique} from './fixtures/unique'

/**
 * The Nachrückliste: an appointment or an event group whose places are all taken puts further
 * sign-ups on a waiting list, in order, and moves them up when a place falls free.
 *
 * <p>Every story makes the appointment or event group it works on, and the people who take its
 * places, rather than reading the seed: the seeded people are shared with every other story running
 * at the same time, and a place one of them gave up elsewhere would move somebody up here.
 */

/** A start and an end some days ahead, at the same hour wherever the station stands, so the date is never past. */
function aheadByDays(days: number, hours = 10): {start: string, end: string} {
    const start = new Date(Date.now() + days * 86400000)
    start.setUTCHours(hours, 0, 0, 0)
    return {start: start.toISOString(), end: new Date(start.getTime() + 3600000).toISOString()}
}

/**
 * A one-time appointment of the story's own that takes sign-ups as they come and has a number of
 * places.
 *
 * @param page   who makes it, somebody who runs the station
 * @param places how many places it has
 */
async function limitedAppointment(page: Page, places: number) {
    const headers = await apiHeaders(page)
    const when = aheadByDays(20)
    const created = await page.request.post('/api/v1/events', {
        headers,
        data: {
            name: unique('Nachrückliste'),
            description: 'Wenige Plätze',
            eventType: 'ONE_TIME',
            startTime: when.start,
            endTime: when.end,
            requiresRegistration: true,
            registrationLimit: places,
        },
    })
    expect(created.ok(), `the organiser made an appointment (${await created.text()})`).toBeTruthy()
    const id: number = (await created.json()).id

    return {
        id,
        /** Signs somebody up as the organiser, onto the waiting list where the date is full. */
        signUp: async (memberId: number, whenFull?: 'QUEUE' | 'OVER_LIMIT') => {
            const registered = await page.request.post(`/api/v1/events/${id}/register`,
                {headers, data: {memberId, whenFull}})
            expect(registered.ok(), `the organiser signed somebody up (${await registered.text()})`).toBeTruthy()
            return await registered.json() as {id: number, status: string}
        },
        registrations: async () => {
            const listed = await page.request.get(`/api/v1/events/${id}/registrations`, {headers})
            return await listed.json() as {id: number, status: string, queuePosition: number | null}[]
        },
        remove: () => page.request.delete(`/api/v1/events/${id}`, {headers}),
    }
}

test.describe('Nachrückliste', () => {
    /**
     * A member signs up for a full appointment, waits on the list with their place in line, and gets
     * the place once the one holding it gives it back.
     */
    test('a member waits on a full appointment and moves up when a place falls free', async ({managerPage, memberPage}) => {
        const appointment = await limitedAppointment(managerPage, 1)
        const holder = await ownMember(managerPage, 'Platzhalter')
        try {
            const held = await appointment.signUp(holder.memberId)
            expect(held.status, 'the first sign-up takes the one place').toBe('ACCEPTED')

            await memberPage.goto(`/station/events/${appointment.id}`)
            await memberPage.getByRole('tab', {name: 'Anmeldungen'}).click()
            const myAnswer = memberPage.locator('[data-testid^="my-answer-"]').first()
            await expect(myAnswer).toHaveText('Noch keine Antwort', {timeout: 15000})

            const join = memberPage.getByTestId('answer-household')
            await expect(join, 'a full appointment offers the waiting list').toContainText('Auf die Nachrückliste')
            await join.click()
            await expect(myAnswer).toHaveText('Nachrückliste, Platz 1', {timeout: 15000})

            const managerHeaders = await apiHeaders(managerPage)
            const given = await managerPage.request.delete(`/api/v1/events/registrations/${held.id}`,
                {headers: managerHeaders})
            expect(given.ok(), `the place is given back (${await given.text()})`).toBeTruthy()

            await memberPage.reload()
            await memberPage.getByRole('tab', {name: 'Anmeldungen'}).click()
            await expect(myAnswer, 'the first in line moved up').toHaveText('Bestätigt', {timeout: 15000})
        } finally {
            await appointment.remove()
        }
    })

    /**
     * Whoever runs the appointment puts the list in another order and moves somebody up by hand,
     * beyond the limit after saying so.
     */
    test('a manager reorders the list and moves somebody up over the limit', async ({managerPage: page}) => {
        const appointment = await limitedAppointment(page, 1)
        const holder = await ownMember(page, 'Platzhalter')
        const first = await ownMember(page, 'Erste')
        const second = await ownMember(page, 'Zweite')
        try {
            await appointment.signUp(holder.memberId)
            const waitingFirst = await appointment.signUp(first.memberId, 'QUEUE')
            const waitingSecond = await appointment.signUp(second.memberId, 'QUEUE')
            expect([waitingFirst.status, waitingSecond.status]).toEqual(['QUEUED', 'QUEUED'])

            await page.goto(`/station/events/${appointment.id}`)
            await page.getByRole('tab', {name: 'Anmeldungen'}).click()
            const list = page.getByTestId('queue-section')
            await expect(list.locator('[data-testid^="queue-entry-"]')).toHaveCount(2, {timeout: 15000})

            await list.getByTestId('move-down').first().click()
            await expect.poll(async () => (await appointment.registrations())
                .find(registration => registration.id === waitingSecond.id)?.queuePosition,
            {message: 'the second in line moved to the front', timeout: 15000}).toBe(1)

            await list.getByTestId(`queue-move-up-member-${waitingFirst.id}`).click()
            await expect(page.getByTestId('full-date-dialog')).toContainText('1 von 1 Plätzen belegt')
            await page.getByTestId('full-date-over-limit').click()

            await expect.poll(async () => (await appointment.registrations())
                .find(registration => registration.id === waitingFirst.id)?.status,
            {message: 'the one moved up holds a place beyond the limit', timeout: 15000}).toBe('ACCEPTED')
            await expect(page.getByTestId('registrations-places')).toHaveText('2 von 1 Plätzen belegt, 1 auf der Nachrückliste')
        } finally {
            await appointment.remove()
        }
    })

    /**
     * An event group whose block takes exactly one of two dates: the full one is marked, and the
     * member picks the one with places left and gets it at once.
     */
    test('a member sees the full alternative of an event group and picks the free one', async ({managerPage, memberPage}) => {
        const headers = await apiHeaders(managerPage)
        const created = await managerPage.request.post('/api/v1/events/groups', {
            headers,
            data: {
                details: {
                    name: unique('Kurs'), description: null, registrationLimit: null, registrationDeadline: null,
                    requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null,
                    partRegistrationLimit: 1, queueOrder: null,
                },
                sets: [{id: null, name: 'Kurstag', minimum: 1, maximum: 1}],
            },
        })
        expect(created.ok(), `the organiser made an event group (${await created.text()})`).toBeTruthy()
        const group = await created.json() as {eventGroup: {id: number}, sets: {id: number}[]}
        const groupId = group.eventGroup.id
        const parts: number[] = []
        try {
            for (const [name, days] of [['Kurs Samstag', 30], ['Kurs Sonntag', 31]] as const) {
                const when = aheadByDays(days)
                const part = await managerPage.request.post(`/api/v1/events/groups/${groupId}/parts/new`, {
                    headers,
                    data: {name, setId: group.sets[0]!.id, startTime: when.start, endTime: when.end},
                })
                expect(part.ok(), `the organiser added a date (${await part.text()})`).toBeTruthy()
                parts.push((await part.json() as {eventId: number}).eventId)
            }
            const [full, free] = parts as [number, number]
            const holder = await ownMember(managerPage, 'Kursplatz')
            const taken = await managerPage.request.post(`/api/v1/events/groups/${groupId}/sign-up`,
                {headers, data: {parts: [full], answers: [], memberId: holder.memberId}})
            expect(taken.ok(), `somebody took the Saturday's only place (${await taken.text()})`).toBeTruthy()

            await memberPage.goto(`/station/events/groups/${groupId}`)
            await memberPage.getByTestId('event-group-sign-up').first().click()
            await expect(memberPage.getByTestId(`event-group-part-full-${full}`)).toHaveText('voll')
            await expect(memberPage.getByTestId(`event-group-part-full-${free}`)).toHaveCount(0)

            await memberPage.getByTestId(`event-group-part-${free}`).click()
            await expect(memberPage.getByTestId('event-group-sign-up-waits')).toHaveCount(0)
            await memberPage.getByTestId('event-group-sign-up-save').click()

            await expect(memberPage.getByTestId('event-group-household').getByTestId('registration-status').first())
                .toHaveText('Bestätigt', {timeout: 15000})
        } finally {
            await managerPage.request.delete(`/api/v1/events/groups/${groupId}`, {headers})
            for (const part of parts) await managerPage.request.delete(`/api/v1/events/${part}`, {headers})
        }
    })

    /**
     * Refusing a sign-up that already holds a place gives that place away, so the page asks first.
     * Turning the question down leaves the place where it was; saying yes refuses the sign-up and a
     * message shows that.
     */
    test('refusing a confirmed sign-up asks first', async ({managerPage: page}) => {
        const appointment = await limitedAppointment(page, 3)
        const holder = await ownMember(page, 'Abgelehnt')
        try {
            const held = await appointment.signUp(holder.memberId)
            expect(held.status, 'the sign-up holds a place').toBe('ACCEPTED')
            const statusOfHeld = async () =>
                (await appointment.registrations()).find(registration => registration.id === held.id)?.status

            await page.goto(`/station/events/${appointment.id}`)
            await page.getByRole('tab', {name: 'Anmeldungen'}).click()
            const deny = page.getByRole('button', {name: 'Ablehnen'}).first()
            await deny.click()

            const confirm = page.getByTestId('deny-accepted-confirm')
            await expect(confirm, 'the page asks before the place is given away').toBeVisible()
            await page.locator('[data-cancel]').last().click()
            await expect(confirm).toHaveCount(0)
            expect(await statusOfHeld(), 'turning the question down keeps the place').toBe('ACCEPTED')

            await deny.click()
            await confirm.click()

            await expect(page.getByTestId('toast').filter({hasText: 'Anmeldung abgelehnt'})).toBeVisible()
            await expect.poll(statusOfHeld, {message: 'saying yes refuses the sign-up', timeout: 15000}).toBe('DENIED')
            await expect(page.getByText('1 abgelehnt')).toBeVisible()
        } finally {
            await appointment.remove()
        }
    })

    /**
     * A sign-up for an event group that waits for confirmation is decided for the whole group, so the
     * open sign-ups list it once, under the event group, leading to the group's page. The dates it
     * chose do not list it a second time.
     */
    test('the open sign-ups list an event group sign-up once, under its event group', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const groupName = unique('Bestätigungskurs')
        const created = await page.request.post('/api/v1/events/groups', {
            headers,
            data: {
                details: {
                    name: groupName, description: null, registrationLimit: null, registrationDeadline: null,
                    requiresConfirmation: true, color: null, minRegistrations: null, thresholdDays: null,
                    partRegistrationLimit: null, queueOrder: null,
                },
                sets: [{id: null, name: 'Kurstage', minimum: 1, maximum: null}],
            },
        })
        expect(created.ok(), `the organiser made an event group (${await created.text()})`).toBeTruthy()
        const group = await created.json() as {eventGroup: {id: number}, sets: {id: number}[]}
        const groupId = group.eventGroup.id
        const parts: number[] = []
        const partNames = [unique('Kurstag Samstag'), unique('Kurstag Sonntag')]
        try {
            for (const [index, name] of partNames.entries()) {
                const when = aheadByDays(40 + index)
                const part = await page.request.post(`/api/v1/events/groups/${groupId}/parts/new`, {
                    headers,
                    data: {name, setId: group.sets[0]!.id, startTime: when.start, endTime: when.end},
                })
                expect(part.ok(), `the organiser added a date (${await part.text()})`).toBeTruthy()
                parts.push((await part.json() as {eventId: number}).eventId)
            }
            const member = await ownMember(page, 'Kursanmeldung')
            const signedUp = await page.request.post(`/api/v1/events/groups/${groupId}/sign-up`,
                {headers, data: {parts, answers: [], memberId: member.memberId}})
            expect(signedUp.ok(), `somebody signed up for both dates (${await signedUp.text()})`).toBeTruthy()

            await page.goto('/station/events/registrations')
            const rows = page.getByTestId('pending-event-group-row').filter({hasText: groupName})
            await expect(rows, 'the event group is listed once').toHaveCount(1, {timeout: 15000})
            await expect(rows).toContainText('1 offen')
            for (const name of partNames) {
                await expect(page.getByText(name), 'its dates do not list the sign-up again').toHaveCount(0)
            }

            await rows.click()
            await expect(page).toHaveURL(new RegExp(`/station/events/groups/${groupId}(\\?[^#]*)?$`))
        } finally {
            await page.request.delete(`/api/v1/events/groups/${groupId}`, {headers})
            for (const part of parts) await page.request.delete(`/api/v1/events/${part}`, {headers})
        }
    })
})
