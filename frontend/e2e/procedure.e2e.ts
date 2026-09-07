/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * A procedure of the story's own with one step in it, left open on its page - which is where
 * creating one lands. Every story makes its own: the stories run side by side, and one of them
 * ticking a step off the procedure another one is reading would be a story about the other story.
 */
async function createProcedureWithStep(page: Page): Promise<string> {
    const procedure = unique('Ablauf')

    await page.goto('/station/procedures')
    await page.getByRole('button', {name: 'Neuer Ablauf'}).click()
    await page.waitForURL(/\/station\/procedures\/create/)

    await page.getByRole('textbox').first().fill(procedure)
    await page.getByRole('button', {name: 'Schritt hinzufügen'}).click()
    await page.getByPlaceholder('Titel').first().fill('Erster Schritt')

    // The button that saves a new procedure carries the same words as the one that opened the page
    // for it, so the story takes the one on the page it is standing on.
    await page.getByRole('button', {name: 'Neuer Ablauf'}).last().click()
    await page.waitForURL(/\/station\/procedures\/\d+$/)

    return procedure
}

/**
 * Creating a procedure opens a page of its own rather than a dialog, because a procedure is a list
 * of steps. Adding one appends an empty row that is written in place, so the story writes into the
 * row rather than answering a dialog about it.
 */
test.describe('Procedures', () => {
    /**
     * Editing a procedure went to an address the backend did not answer on, so the save reported
     * nothing and changed nothing. The story walks the same path a person does: open one, change
     * its name, and find the new name after a reload.
     */
    test('a procedure is renamed', async ({managerPage: page}) => {
        const renamed = unique('Ablauf')

        await page.goto('/station/procedures')

        // The list navigates by click handler, so its rows carry an identifier to aim at.
        await page.getByTestId('procedure-entry').first().click()
        await page.waitForURL(/\/station\/procedures\/\d+/)
        await page.goto(`${page.url()}/edit`)

        await page.getByRole('textbox').first().fill(renamed)
        await page.getByRole('button', {name: 'Speichern'}).first().click()

        await page.reload()
        await expect(page.getByRole('textbox').first()).toHaveValue(renamed)
    })

    test('a procedure is created', async ({managerPage: page}) => {
        const procedure = await createProcedureWithStep(page)

        await page.goto('/station/procedures')
        await expect(page.getByText(procedure).first()).toBeVisible()
    })

    /**
     * A procedure exists to be worked through. The story makes its own, ticks its step off and
     * reloads: a tick that does not survive one was never recorded, and the person following the
     * procedure would do the step twice.
     */
    test('a step of a procedure is ticked off and stays ticked', async ({managerPage: page}) => {
        await createProcedureWithStep(page)

        const check = page.getByRole('button', {name: 'Erledigt', exact: true}).first()
        await expect(check).toBeVisible()
        await check.click()

        await expect(page.getByRole('button', {name: 'Zurücksetzen'}).first()).toBeVisible()

        await page.reload()
        await expect(page.getByRole('button', {name: 'Zurücksetzen'}).first()).toBeVisible()
    })

    /**
     * A template exists so the steps do not have to be typed again. The story loads one, reads the
     * step it brought along, and looks for that step in the procedure it then creates.
     */
    test('a template brings its steps into a new procedure', async ({managerPage: page}) => {
        const procedure = unique('Ablauf')

        await page.goto('/station/procedures/create')

        await page.locator('select').first().selectOption({index: 1})

        const firstStep = page.getByPlaceholder('Titel').first()
        await expect(firstStep).toBeVisible()
        const stepTitle = await firstStep.inputValue()
        expect(stepTitle).not.toBe('')

        await page.getByRole('textbox').first().fill(procedure)
        await page.getByRole('button', {name: 'Neuer Ablauf'}).last().click()

        await page.waitForURL(/\/station\/procedures\/\d+$/)
        await expect(page.getByText(stepTitle).first()).toBeVisible()
    })

    test('the procedure templates are reachable', async ({managerPage: page}) => {
        await page.goto('/station/procedures/templates')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * The preparation for an evening starts from who is coming to it.
     *
     * <p>The sign-ups are arranged through the endpoints, because the story is about what the menu
     * in the sign-ups tab does and not about three people clicking a button. What it asserts is the
     * three things that make the list usable rather than decorative: the people who hold a place are
     * on it, the steps came from a template, and pressing the same entry again offers this list
     * instead of building a second one beside it.
     */
    test('a procedure is prepared for the people who are coming', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const appointment = unique('Ablaufabend')

        const created = await page.request.post('/api/v1/events', {
            headers,
            data: {
                name: appointment,
                description: 'Vorbereitung aus den Anmeldungen',
                eventType: 'ONE_TIME',
                startTime: new Date(Date.now() + 27 * 86400000).toISOString(),
                endTime: new Date(Date.now() + 27 * 86400000 + 3600000).toISOString(),
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

        for (const person of people) {
            const signedUp = await page.request.post(`/api/v1/events/${eventId}/register`,
                {headers, data: {memberId: person.id}})
            expect(signedUp.ok(), `${person.name} signed up (${await signedUp.text()})`).toBeTruthy()
        }

        await page.goto(`/station/events/${eventId}`)
        await page.getByRole('button', {name: 'Anmeldungen'}).click()

        await page.getByRole('button', {name: 'Aus den Anmeldungen'}).click()
        await page.getByTestId('signup-procedure-entry').click()

        // A procedure without a template has no steps, so the dialog asks for one before anything else.
        const template = page.getByTestId('signup-procedure-template')
        await expect(template).toBeVisible()
        await template.selectOption({index: 1})
        await expect(page.getByTestId('signup-procedure-name'), 'the appointment names the list')
            .toHaveValue(new RegExp(appointment))

        await page.getByTestId('signup-procedure-submit').click()
        await page.waitForURL(/\/station\/procedures\/\d+$/)
        const procedureId = page.url().match(/procedures\/(\d+)/)?.[1]

        await expect(page.getByText(people[0]!.name).first(), 'the first place holder is on it').toBeVisible()
        await expect(page.getByText(people[1]!.name).first(), 'the second place holder is on it').toBeVisible()
        await expect(page.getByTestId('procedure-appointment-link'), 'and it leads back to the evening')
            .toBeVisible()

        const detail = await page.request.get(`/api/v1/procedures/${procedureId}`, {headers})
        expect(detail.ok(), `the procedure reads back (${await detail.text()})`).toBeTruthy()
        const body = await detail.json()
        expect(body.items.length, 'the template brought its steps along').toBeGreaterThan(0)
        expect(body.procedure.isPublic, 'a private list would be closed to the people on it').toBe(true)
        expect(body.items.every((item: {userAssigned: boolean}) => item.userAssigned),
            'every step is one the people on the list may tick').toBe(true)

        await page.goto(`/station/events/${eventId}`)
        await page.getByRole('button', {name: 'Anmeldungen'}).click()
        await page.getByRole('button', {name: 'Aus den Anmeldungen'}).click()
        await page.getByTestId('signup-procedure-entry').click()

        await expect(page.getByTestId('signup-procedure-existing'),
            'a second press offers what is already there').toBeVisible()

        await page.request.delete(`/api/v1/procedures/${procedureId}`, {headers})
        await page.request.delete(`/api/v1/events/${eventId}`, {headers})
    })
})
