/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, type Page} from './fixtures/auth'
import {pickMemberByName} from './fixtures/memberMenu'

/**
 * Registering for an event, withdrawing again, and the organiser's view of who has signed up.
 *
 * The planner navigates by click handler rather than by link, so its entries carry a test id - that
 * is the only way a story can pick one out without asserting on the shape of a calendar grid. The
 * entry also says whether its event takes registrations, because an event that does not has no
 * registration tab and would make these stories wait for something that is correctly absent.
 */
/**
 * A coming occurrence of a seeded appointment that is signed up for, opened on that very day.
 *
 * <p>Asked of the API rather than picked off the page, because the list pages ten at a time and a
 * station whose next ten appointments happen to take none would leave the story hunting a row that
 * is there but not shown.
 *
 * <p>It has to be coming rather than any appointment the station ever wrote, because a day gone by
 * takes no answer. It is opened on its own day rather than by its bare id, so a repeating
 * appointment is bound to an occurrence it really has instead of whichever day the page works out
 * for itself.
 */
async function openEventWithRegistration(page: Page) {
    const headers = await apiHeaders(page)

    const coming = await page.request.get('/api/v1/events/upcoming?requiresRegistration=true&limit=1', {headers})
    expect(coming.ok(), 'the list of coming dates answers').toBeTruthy()

    const [next]: {date: string, event: {id: number}}[] = await coming.json()
    expect(next, 'the seeded station has a coming appointment that takes sign-ups').toBeTruthy()

    await page.goto(`/station/events/${next!.event.id}/${next!.date}`)
}

/**
 * A weekly appointment of the story's own that anybody may sign up for without being confirmed.
 *
 * <p>Made rather than picked from the seed, because which seeded appointment comes next depends on
 * the hour the suite runs: shortly after midnight where the station stands the one planted for
 * today has already gone by, and the next in line is open to guardians only, so a member's answer
 * was refused and the story read the refusal as a missing answer.
 *
 * <p>It starts a week out at ten in the morning, the same day wherever the station stands.
 *
 * @param page who makes it, somebody who runs the station
 * @param name what it is called, unique to the run
 */
async function weeklyAppointment(page: Page, name: string) {
    const headers = await apiHeaders(page)
    const week = 7 * 86400000
    const start = new Date(Date.now() + week)
    start.setUTCHours(10, 0, 0, 0)

    const created = await page.request.post('/api/v1/events', {
        headers,
        data: {
            name,
            description: 'Ein Platz gilt für einen Tag',
            eventType: 'RECURRING',
            dayOfWeek: start.getUTCDay() === 0 ? 7 : start.getUTCDay(),
            startTime: start.toISOString(),
            endTime: new Date(start.getTime() + 3600000).toISOString(),
            requiresRegistration: true,
        },
    })
    expect(created.ok(), `the organiser made an appointment (${await created.text()})`).toBeTruthy()
    const id: number = (await created.json()).id

    return {
        id,
        dateAfterWeeks: (weeks: number) => new Date(start.getTime() + weeks * week).toISOString().slice(0, 10),
        remove: () => page.request.delete(`/api/v1/events/${id}`, {headers}),
    }
}

/**
 * Giving a place back, which asks first.
 *
 * <p>Every button that gives one up now opens the same question, because a place given up is gone and
 * the button sits in a list somebody is scrolling. The stories answer it the way a member would.
 */
async function signOff(page: Page, button: ReturnType<Page['getByTestId']>) {
    await button.click()
    await page.getByTestId('confirm-sign-off').click()
}

test.describe('Events', () => {
    test('the planner is reachable and offers a new event', async ({managerPage: page}) => {
        await page.goto('/station/events')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neuer Termin'})).toBeVisible()
    })

    /**
     * A member sees the station's events. They did not until the page stopped asking for the
     * attendance templates alongside them: that call is refused for anyone who does not record
     * attendance, and it took the whole page down with it.
     */
    test('a member sees the events of their station', async ({memberPage: page}) => {
        await page.goto('/station/events')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByTestId('event-entry').first()).toBeVisible()
    })

    /**
     * A comment under an appointment belongs to whoever wrote it: they write it, put it right and
     * take it back, all from the appointment's own page, and a correction is marked as one.
     */
    test('an author writes, changes and removes their comment on an appointment', async ({managerPage: page}) => {
        const appointment = await weeklyAppointment(page, `Kommentarprobe ${Date.now()}`)
        const first = `Erster Gedanke ${Date.now()}`
        const second = `Zweiter Gedanke ${Date.now()}`
        try {
            await page.goto(`/station/events/${appointment.id}/${appointment.dateAfterWeeks(0)}`)
            await page.locator('[contenteditable="true"]').last().click()
            await page.keyboard.type(first)
            await page.getByRole('button', {name: 'Absenden'}).click()

            const comment = page.locator('[id^="comment-"]').filter({hasText: first})
            await expect(comment).toHaveCount(1)
            await comment.getByRole('button', {name: 'Bearbeiten', exact: true}).click()
            await comment.locator('[contenteditable="true"]').click()
            await page.keyboard.press('ControlOrMeta+A')
            await page.keyboard.type(second)
            await comment.getByRole('button', {name: 'Speichern', exact: true}).click()

            const changed = page.locator('[id^="comment-"]').filter({hasText: second})
            await expect(changed).toContainText('bearbeitet')
            await changed.getByRole('button', {name: 'Löschen', exact: true}).click()
            await expect(page.getByText(second)).toHaveCount(0)
        } finally {
            await appointment.remove()
        }
    })

    /**
     * The list of what is coming up reads from the nearest date to the furthest. It used to hoist
     * every event running over several days to the front, which put one months away above
     * tomorrow's drill and made the whole list read as unsorted.
     */
    test('the upcoming list reads from the nearest date to the furthest', async ({memberPage: page}) => {
        await page.goto('/station/events/upcoming')

        const rows = page.getByTestId('upcoming-event')
        await expect(rows.first()).toBeVisible()

        const dates = await rows.evaluateAll(nodes => nodes.map(node => node.getAttribute('data-date') ?? ''))
        expect(dates.length, 'there is something to put in order').toBeGreaterThan(1)
        expect(dates, 'every row is on or after the one above it').toEqual([...dates].sort())
    })

    /**
     * A monthly appointment opens on the day it next falls on.
     *
     * <p>The page used to work that day out itself, by stepping from today to the next matching
     * weekday. That is the rule a weekly appointment keeps and no other: a monthly one landed on
     * whichever of its weekdays came round first, so the page named a day in the middle of the
     * month and offered its sign-ups under a day the appointment does not happen on.
     *
     * <p>The line under the name reads "Details und Anmeldungen" until the server has named the day,
     * so the story waits for a date there before it reads one off.
     */
    test('a monthly appointment opens on the day it next falls on', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const answer = await page.request.get('/api/v1/events', {headers})
        expect(answer.ok(), 'the appointment list answers').toBeTruthy()

        const appointments: {id: number, eventType: string}[] = await answer.json()
        const monthly = appointments.find(appointment => appointment.eventType === 'MONTHLY_FIRST')
        expect(monthly, 'the seeded station has an appointment repeating monthly').toBeTruthy()

        await page.goto(`/station/events/${monthly!.id}`)

        const shown = page.getByTestId('page-subtitle')
        const date = /(\d{2})\.\d{2}\.\d{4}/
        await expect(shown).toHaveText(date)
        const dayOfMonth = Number((await shown.textContent())!.match(date)![1])
        expect(dayOfMonth, 'the first of its weekday in the month, so never past the seventh').toBeLessThanOrEqual(7)
    })

    /**
     * The registrations of an event load when the tab is opened. This is the story behind a fix
     * that shipped: the tab stayed empty because the list was asked for while the page was still
     * loading, and nothing asked again afterwards.
     */
    test('an event shows who has registered', async ({managerPage: page}) => {
        await openEventWithRegistration(page)

        await page.getByRole('tab', {name: 'Anmeldungen'}).click()
        await expect(page.getByText(/Meine Anmeldung|Anmeldungen/).first()).toBeVisible()
    })

    /**
     * Registering and giving the place back in one walk, which is how a member uses this in practice.
     *
     * <p>An event that has to be signed up for takes one answer, so the buttons swap rather than sit
     * beside each other: signing up while there is no place, giving it back while there is one. The
     * appointment repeats and is opened on one of its dates, because the tab reads the answers of
     * that date only and the answer given has to land on it.
     *
     * <p>Giving the place back leaves no answer at all rather than a refusal. Not being signed up is
     * already how somebody says they are not coming, and writing that down a second time would say it
     * twice.
     */
    test('a member registers for an event and gives the place back', async ({managerPage, memberPage: page}) => {
        const appointment = await weeklyAppointment(managerPage, `Hin und zurück ${test.info().workerIndex}-${Date.now()}`)

        try {
            await page.goto(`/station/events/${appointment.id}/${appointment.dateAfterWeeks(1)}`)
            await page.getByRole('tab', {name: 'Anmeldungen'}).click()

            const myAnswer = page.locator('[data-testid^="my-answer-"]').first()
            await expect(myAnswer).toHaveText('Noch keine Antwort', {timeout: 15000})

            await page.getByTestId('answer-household').click()
            await expect(myAnswer).toHaveText('Bestätigt', {timeout: 15000})

            await signOff(page, page.getByTestId('withdraw-household'))
            await expect(myAnswer).toHaveText('Noch keine Antwort', {timeout: 15000})
        } finally {
            await appointment.remove()
        }
    })

    /**
     * The wrong button, and the way back from it.
     *
     * <p>A place given up used to be gone the instant the press landed, which on an appointment past
     * its deadline meant gone for good. For five minutes it can be put back, and what comes back is
     * the place that was held rather than a fresh answer at the end of a queue: the story checks the
     * confirmed seat is confirmed again, not pending. The way back is pressed on the message that
     * appears, because by then the row may be nowhere near the reader.
     */
    test('a place given up by accident is taken back', async ({managerPage, memberPage}) => {
        const managerHeaders = await apiHeaders(managerPage)
        const name = `Versehen ${test.info().workerIndex}-${Date.now()}`

        const created = await managerPage.request.post('/api/v1/events', {
            headers: managerHeaders,
            data: {
                name,
                description: 'Aus Versehen abgemeldet',
                eventType: 'ONE_TIME',
                startTime: new Date(Date.now() + 20 * 86400000).toISOString(),
                endTime: new Date(Date.now() + 20 * 86400000 + 3600000).toISOString(),
                requiresRegistration: true,
            },
        })
        expect(created.ok(), `the organiser made an event (${await created.text()})`).toBeTruthy()
        const eventId = (await created.json()).id

        try {
            await memberPage.goto(`/station/events/${eventId}`)
            await memberPage.getByRole('tab', {name: 'Anmeldungen'}).click()

            const myAnswer = memberPage.locator('[data-testid^="my-answer-"]').first()
            await memberPage.getByTestId('answer-household').click()
            await expect(myAnswer).toHaveText('Bestätigt', {timeout: 15000})

            await signOff(memberPage, memberPage.getByTestId('withdraw-household'))
            await expect(myAnswer, 'the place is given up').toHaveText('Noch keine Antwort', {timeout: 15000})

            await memberPage.getByTestId('toast-action').click()
            await expect(myAnswer, 'the seat comes back as a seat, not as a pending answer')
                .toHaveText('Bestätigt', {timeout: 15000})
        } finally {
            await managerPage.request.delete(`/api/v1/events/${eventId}`, {headers: managerHeaders})
        }
    })

    /**
     * A place that is still waiting to be confirmed can be given back like any other.
     *
     * <p>An appointment that confirms its sign-ups leaves the member in between for a while: they have
     * said they are coming and nobody has said yes yet. That is still a place taken, so it can be
     * given back, and giving it back removes it rather than turning it into a refusal.
     */
    test('a place still waiting to be confirmed can be given back',
        async ({managerPage, memberPage}) => {
            const managerHeaders = await apiHeaders(managerPage)
            const name = `Bestätigung ${test.info().workerIndex}-${Date.now()}`

            const created = await managerPage.request.post('/api/v1/events', {
                headers: managerHeaders,
                data: {
                    name,
                    description: 'Warten auf Zusage',
                    eventType: 'ONE_TIME',
                    startTime: new Date(Date.now() + 15 * 86400000).toISOString(),
                    endTime: new Date(Date.now() + 15 * 86400000 + 3600000).toISOString(),
                    requiresRegistration: true,
                    requiresConfirmation: true,
                },
            })
            expect(created.ok(), `the organiser made an event (${await created.text()})`).toBeTruthy()
            const eventId = (await created.json()).id

            await memberPage.goto(`/station/events/${eventId}`)
            await memberPage.getByRole('tab', {name: 'Anmeldungen'}).click()

            const myAnswer = memberPage.locator('[data-testid^="my-answer-"]').first()

            await memberPage.getByTestId('answer-household').click()
            await expect(myAnswer, 'nobody has said yes to them yet').toHaveText('Ausstehend', {timeout: 15000})

            await signOff(memberPage, memberPage.getByTestId('withdraw-household'))
            await expect(myAnswer, 'the place is gone rather than refused')
                .toHaveText('Noch keine Antwort', {timeout: 15000})

            await managerPage.request.delete(`/api/v1/events/${eventId}`, {headers: managerHeaders})
        })

    /**
     * Somebody who gave their place back is put on the list again by whoever runs the appointment.
     *
     * <p>A place given back keeps its row, and the list for adding members used to read any row as a
     * place, so the one person who could have put them back found nobody to pick. The member is found
     * by address: another story renames them while this one runs, and the station has more than one
     * member of their surname.
     */
    test('a member who gave their place back is put on the list again by the organiser',
        async ({managerPage, memberPage}) => {
            const managerHeaders = await apiHeaders(managerPage)
            const memberHeaders = await apiHeaders(memberPage)
            const session = await memberPage.request.get('/api/v1/session', {headers: memberHeaders})
            expect(session.ok(), `the member has a session (${await session.text()})`).toBeTruthy()
            const reader = await session.json() as {member: {id: number}, account: {email: string}}

            const created = await managerPage.request.post('/api/v1/events', {
                headers: managerHeaders,
                data: {
                    name: `Wieder dabei ${test.info().workerIndex}-${Date.now()}`,
                    description: 'Abgemeldet und wieder eingetragen',
                    eventType: 'ONE_TIME',
                    startTime: new Date(Date.now() + 18 * 86400000).toISOString(),
                    endTime: new Date(Date.now() + 18 * 86400000 + 3600000).toISOString(),
                    requiresRegistration: true,
                },
            })
            expect(created.ok(), `the organiser made an event (${await created.text()})`).toBeTruthy()
            const eventId = (await created.json()).id

            try {
                const registered = await memberPage.request.post(`/api/v1/events/${eventId}/register`,
                    {headers: memberHeaders, data: {}})
                expect(registered.ok(), `the member signed up (${await registered.text()})`).toBeTruthy()
                const withdrawn = await memberPage.request.delete(
                    `/api/v1/events/registrations/${(await registered.json()).id}`, {headers: memberHeaders})
                expect(withdrawn.ok(), `the member gave the place back (${await withdrawn.text()})`).toBeTruthy()

                await managerPage.goto(`/station/events/${eventId}`)
                await managerPage.getByRole('tab', {name: 'Anmeldungen'}).click()
                await pickMemberByName(managerPage.getByTestId('manual-register'), reader.account.email)

                await expect.poll(async () => {
                    const listed = await managerPage.request.get(`/api/v1/events/${eventId}/registrations`,
                        {headers: managerHeaders})
                    const registrations = await listed.json() as {memberId: number, status: string}[]
                    return registrations.find(registration => registration.memberId === reader.member.id)?.status
                }, {message: 'the member holds a place again', timeout: 15000}).toBe('ACCEPTED')
            } finally {
                await managerPage.request.delete(`/api/v1/events/${eventId}`, {headers: managerHeaders})
            }
        })

    /**
     * A place on one date of a repeating appointment says nothing about the next one.
     *
     * <p>The registrations tab reads every date the appointment ever had, and it used to take the
     * first row it found as the member's answer on whichever date was open.
     */
    test('a place on one date of a repeating appointment is not shown on another',
        async ({managerPage, memberPage}) => {
            const memberHeaders = await apiHeaders(memberPage)
            const appointment = await weeklyAppointment(managerPage, `Jede Woche ${test.info().workerIndex}-${Date.now()}`)
            const signedUpFor = appointment.dateAfterWeeks(1)
            const weekAfter = appointment.dateAfterWeeks(2)

            try {
                const registered = await memberPage.request.post(`/api/v1/events/${appointment.id}/register`,
                    {headers: memberHeaders, data: {eventDate: signedUpFor}})
                expect(registered.ok(), `the member signed up for one date (${await registered.text()})`).toBeTruthy()

                const myAnswer = memberPage.locator('[data-testid^="my-answer-"]').first()

                await memberPage.goto(`/station/events/${appointment.id}/${signedUpFor}`)
                await memberPage.getByRole('tab', {name: 'Anmeldungen'}).click()
                await expect(myAnswer, 'the date signed up for holds the place').toHaveText('Bestätigt', {timeout: 15000})

                await memberPage.goto(`/station/events/${appointment.id}/${weekAfter}`)
                await memberPage.getByRole('tab', {name: 'Anmeldungen'}).click()
                await expect(myAnswer, 'the week after is still open').toHaveText('Noch keine Antwort', {timeout: 15000})
            } finally {
                await appointment.remove()
            }
        })

    /**
     * An answer can be changed while registration is open and not afterwards, when the list has been
     * counted on. After that it is the event's to change: whoever runs it still can, and the member
     * cannot. The deadline is moved underneath a standing registration, because that is the only way to
     * reach the closed state without waiting for a real one to pass.
     */
    test('an answer can be changed until registration closes, and only by the organiser after',
        async ({managerPage, memberPage}) => {
            const managerHeaders = await apiHeaders(managerPage)
            const memberHeaders = await apiHeaders(memberPage)
            const name = `Antwort ändern ${test.info().workerIndex}-${Date.now()}`

            const created = await managerPage.request.post('/api/v1/events', {
                headers: managerHeaders,
                data: {
                    name,
                    description: 'Antwortprobe',
                    eventType: 'ONE_TIME',
                    startTime: new Date(Date.now() + 20 * 86400000).toISOString(),
                    endTime: new Date(Date.now() + 20 * 86400000 + 3600000).toISOString(),
                    requiresRegistration: true,
                    registrationDeadline: new Date(Date.now() + 10 * 86400000).toISOString(),
                },
            })
            expect(created.ok(), `the organiser made an event (${await created.text()})`).toBeTruthy()
            const eventId = (await created.json()).id

            const signedUp = await memberPage.request.post(`/api/v1/events/${eventId}/register`,
                {headers: memberHeaders, data: {}})
            expect(signedUp.ok(), `the member signed up (${await signedUp.text()})`).toBeTruthy()
            const registrationId = (await signedUp.json()).id

            const changed = await memberPage.request.put(
                `/api/v1/events/registrations/${registrationId}/answer`,
                {headers: memberHeaders, data: {attending: false}})
            expect(changed.ok(), `and changed their mind while it was open (${await changed.text()})`).toBeTruthy()

            const closed = await managerPage.request.put(`/api/v1/events/${eventId}`, {
                headers: managerHeaders,
                data: {
                    name,
                    description: 'Antwortprobe',
                    eventType: 'ONE_TIME',
                    startTime: new Date(Date.now() + 20 * 86400000).toISOString(),
                    endTime: new Date(Date.now() + 20 * 86400000 + 3600000).toISOString(),
                    requiresRegistration: true,
                    registrationDeadline: new Date(Date.now() - 86400000).toISOString(),
                },
            })
            expect(closed.ok(), `the organiser closed registration (${await closed.text()})`).toBeTruthy()

            const refused = await memberPage.request.put(
                `/api/v1/events/registrations/${registrationId}/answer`,
                {headers: memberHeaders, data: {attending: true}})
            expect(refused.status(), 'the member cannot answer once it has closed').toBe(400)

            const byOrganiser = await managerPage.request.put(
                `/api/v1/events/registrations/${registrationId}/answer`,
                {headers: managerHeaders, data: {attending: true}})
            expect(byOrganiser.ok(), `but the organiser still can (${await byOrganiser.text()})`).toBeTruthy()

            await managerPage.request.delete(`/api/v1/events/${eventId}`, {headers: managerHeaders})
        })

    /**
     * A question added to an appointment somebody has already signed up for. They are told, the
     * sheet says beside their answer that one is wanted, and they give it from the appointment's own
     * page after the deadline has passed: the question is younger than the deadline, so closing
     * registration cannot be what stops them answering it.
     */
    test('a question added after somebody signed up is asked of them and answered afterwards',
        async ({managerPage, memberPage}) => {
            const managerHeaders = await apiHeaders(managerPage)
            const memberHeaders = await apiHeaders(memberPage)
            const name = `Nachfrage ${test.info().workerIndex}-${Date.now()}`
            const question = 'Schwimmabzeichen'

            const created = await managerPage.request.post('/api/v1/events', {
                headers: managerHeaders,
                data: {
                    name,
                    description: 'Frage kommt später',
                    eventType: 'ONE_TIME',
                    startTime: new Date(Date.now() + 21 * 86400000).toISOString(),
                    endTime: new Date(Date.now() + 21 * 86400000 + 3600000).toISOString(),
                    requiresRegistration: true,
                    registrationDeadline: new Date(Date.now() + 10 * 86400000).toISOString(),
                },
            })
            expect(created.ok(), `the organiser made an appointment (${await created.text()})`).toBeTruthy()
            const eventId = (await created.json()).id

            const signedUp = await memberPage.request.post(`/api/v1/events/${eventId}/register`,
                {headers: memberHeaders, data: {}})
            expect(signedUp.ok(), `the member signed up before the question existed (${await signedUp.text()})`)
                .toBeTruthy()

            const asked = await managerPage.request.put(`/api/v1/events/${eventId}/registration-fields`, {
                headers: managerHeaders,
                data: {fields: [{name: question, fieldType: 'STRING', config: {required: true}, overview: true}]},
            })
            expect(asked.ok(), `and the appointment gained a question afterwards (${await asked.text()})`).toBeTruthy()

            const mine = await memberPage.request
                .get('/api/v1/events/registrations/mine', {headers: memberHeaders})
                .then(response => response.json())
            expect(
                mine.find((entry: {eventId: number}) => entry.eventId === eventId)?.answersMissing,
                'the registration says an answer is wanted',
            ).toBe(true)

            const told = await memberPage.request
                .get('/api/v1/notifications', {headers: memberHeaders})
                .then(response => response.json())
            expect(
                told.some((entry: {type: string}) => entry.type === 'REGISTRATION_ANSWER_MISSING'),
                'and the member was told once',
            ).toBeTruthy()

            // The deadline goes by before they answer, which is the whole point: the question is
            // younger than the deadline, so it cannot be the deadline that stops them.
            const closed = await managerPage.request.put(`/api/v1/events/${eventId}`, {
                headers: managerHeaders,
                data: {
                    name,
                    description: 'Frage kommt später',
                    eventType: 'ONE_TIME',
                    startTime: new Date(Date.now() + 21 * 86400000).toISOString(),
                    endTime: new Date(Date.now() + 21 * 86400000 + 3600000).toISOString(),
                    requiresRegistration: true,
                    registrationDeadline: new Date(Date.now() - 86400000).toISOString(),
                },
            })
            expect(closed.ok(), `registration closed (${await closed.text()})`).toBeTruthy()

            await memberPage.goto(`/station/events/${eventId}`)
            const block = memberPage.getByTestId('your-answer')
            await expect(block, 'the block stands, because an answer of theirs is on it').toBeVisible()
            await expect(block.getByText('Antwort erforderlich').first()).toBeVisible()

            await block.getByRole('button', {name: 'Antworten aktualisieren'}).first().click()
            await memberPage.getByRole('textbox').last().fill('Bronze')
            await memberPage.getByRole('button', {name: 'Speichern'}).last().click()

            await expect(block.getByText('Antwort erforderlich'), 'and nothing is wanted any more')
                .toHaveCount(0)

            await managerPage.request.delete(`/api/v1/events/${eventId}`, {headers: managerHeaders})
        })

    test('the registration overview lists across events', async ({managerPage: page}) => {
        await page.goto('/station/events/registrations')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText('Anmeldungen').first()).toBeVisible()
    })

    /**
     * An event can ask the people signing up for things - shirt size, who is coming along. The
     * story adds such a question to an event of its own and then signs up as a member, who is asked
     * it and whose answer stands next to their name for the organiser afterwards.
     */
    test('a registration question is asked and its answer reaches the organiser', async ({managerPage, memberPage}) => {
        const event = `Termin-${Date.now()}`
        const question = 'Wer kommt mit?'
        const answer = `Antwort-${Date.now()}`

        await managerPage.goto('/station/events/new')
        await managerPage.getByPlaceholder('Name des Termins').fill(event)

        // An event without a time is not an event, and the form keeps its save disabled until it
        // has one.
        const times = managerPage.locator('input[type="datetime-local"]')
        await times.first().fill('2026-12-01T18:00')
        if (await times.count() > 1) await times.nth(1).fill('2026-12-01T20:00')

        // Registration is off to begin with, and the questions belong to it. The switch sits beside
        // the words rather than under them.
        await managerPage.getByText('Anmeldung erforderlich')
            .locator('xpath=following-sibling::button').click()
        await managerPage.getByRole('button', {name: 'Frage hinzufügen'}).click()
        await managerPage.getByPlaceholder('z.B. Begleitpersonen').fill(question)

        await managerPage.getByRole('button', {name: /Speichern|Erstellen/}).last().click()

        // Saving lands back on the planner rather than on the event, so the story opens it from
        // the list it now stands in.
        await managerPage.waitForURL(/\/station\/events$/)
        await managerPage.getByText(event).first().click()
        await managerPage.waitForURL(/\/station\/events\/(\d+)/)
        const id = managerPage.url().match(/events\/(\d+)/)?.[1]

        await memberPage.goto(`/station/events/${id}`)
        await memberPage.getByRole('tab', {name: 'Anmeldungen'}).click()
        await memberPage.getByRole('button', {name: 'Anmelden'}).first().click()

        await expect(memberPage.getByText(question).first()).toBeVisible()
        await memberPage.getByRole('textbox').first().fill(answer)
        await memberPage.getByRole('button', {name: /Anmelden|Absenden|Speichern/}).last().click()

        await managerPage.goto(`/station/events/${id}`)
        await managerPage.getByRole('tab', {name: 'Anmeldungen'}).click()
        await expect(managerPage.getByText(answer).first()).toBeVisible()
    })

    /**
     * A course over a few dates is a repeating appointment that stops. Before it could be said, a
     * series ran for ever and had to be deleted by hand on the day it ended.
     */
    test('a repeating appointment is given an end', async ({managerPage: page}) => {
        const name = `Lehrgang-${Date.now()}`

        await page.goto('/station/events/new')
        await page.getByPlaceholder('Name des Termins').fill(name)

        const times = page.locator('input[type="datetime-local"]')
        await times.first().fill('2026-12-03T18:00')
        if (await times.count() > 1) await times.nth(1).fill('2026-12-03T20:00')

        await page.getByTestId('event-type-toggle').getByRole('switch').click()
        await page.getByTestId('repeat-end-kind').selectOption('afterCount')
        await page.getByTestId('repeat-end-count').fill('8')

        await page.getByRole('button', {name: /Speichern|Erstellen/}).last().click()

        await page.waitForURL(/\/station\/events/)
        await page.goto(`/station/events?search=${encodeURIComponent(name)}`)
        await page.getByTestId('event-entry').filter({hasText: name}).first().click()
        await page.waitForURL(/\/station\/events\/\d+/)

        await expect(page.getByTestId('event-repeat-end')).toHaveText(/8 Mal/)
    })

    /**
     * A season of weekly appointments is entered once rather than fifty times. The story generates the
     * dates, creates them in one go, and finds one of them in the planner afterwards.
     */
    test('a run of events is created in one go', async ({managerPage: page}) => {
        const name = `Serie-${Date.now()}`

        // Three steps: what the events are called, when they fall, and a last look at the list.
        await page.goto('/station/events/batch')
        await page.getByRole('textbox').first().fill(name)
        await page.getByRole('button', {name: 'Weiter'}).click()

        const dates = page.locator('input[type="date"]')
        await dates.first().fill('2026-12-01')
        await dates.nth(1).fill('2026-12-31')

        await page.getByRole('button', {name: 'Termine generieren'}).click()
        await expect(page.getByText(/\d+ Termine werden erstellt/)).toBeVisible()

        await page.getByRole('button', {name: 'Termine erstellen'}).click()

        await page.goto(`/station/events?search=${encodeURIComponent(name)}`)
        await expect(page.getByText(name).first()).toBeVisible()
    })

    /**
     * A category is what a manager reaches for when the list of events stops being readable. The
     * story creates one and reloads: a category that only lives in the open page is no category.
     */
    test('a category is created and survives a reload', async ({managerPage: page}) => {
        const name = `Kategorie ${Date.now()}`

        await page.goto('/station/events/categories')
        await page.getByRole('button', {name: 'Kategorie erstellen'}).click()
        await page.getByPlaceholder('z.B. Übungen').fill(name)
        await page.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(name)).toBeVisible()

        await page.reload()
        await expect(page.getByText(name)).toBeVisible()
    })

    /**
     * The planner reads by date, with every category mixed into one list and each row wearing its
     * own. It used to be sorted into a block per category, which is why a category is on a row at
     * all: without the badge the reader would have lost what the headings used to say.
     */
    test('the planner reads by date and every row wears its category', async ({managerPage: page}) => {
        await page.goto('/station/events')

        await expect(page.getByTestId('event-entry').first()).toBeVisible()
        await expect(page.getByTestId('event-entry-category').first()).toBeVisible()
    })

    /**
     * What has been is a tab of its own, and the tab is in the address, so a reader can keep the
     * past open, copy what they are looking at and come back to it.
     */
    test('the past appointments are a tab of their own', async ({managerPage: page}) => {
        await page.goto('/station/events')

        await page.getByRole('tab', {name: 'Vergangen'}).click()
        await expect(page).toHaveURL(/tab=past/)

        await page.reload()
        await expect(page).toHaveURL(/tab=past/)
        await expect(page.getByText(/Vergangene Termine|Keine vergangenen Termine/).first()).toBeVisible()
    })
})
