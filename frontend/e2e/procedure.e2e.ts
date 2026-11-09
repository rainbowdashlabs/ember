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
 * The saving button carries the same words as the one that opened the page, so the last one is taken.
 */
async function createProcedureWithStep(page: Page): Promise<string> {
    const procedure = unique('Ablauf')

    await page.goto('/station/procedures')
    await page.getByRole('button', {name: 'Neuer Ablauf'}).click()
    await page.waitForURL(/\/station\/procedures\/create/)
    await page.getByTestId('procedure-start-blank').click()
    await page.waitForURL(/blank=1/)

    await page.getByRole('textbox').first().fill(procedure)
    await chooseAbout(page)
    await page.getByRole('button', {name: 'Schritt hinzufügen'}).click()
    await page.getByPlaceholder('Titel').first().fill('Erster Schritt')

    await page.getByRole('button', {name: 'Neuer Ablauf'}).last().click()
    await page.waitForURL(/\/station\/procedures\/\d+$/)

    return procedure
}

/**
 * Picks the first member a new procedure can be about, which every new one has to name: the picker opens
 * on a click and takes the highlighted row on Enter.
 */
async function chooseAbout(page: Page): Promise<void> {
    await page.getByTestId('procedure-about').getByTestId('member-select-trigger').click()
    await page.keyboard.press('Enter')
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

        await page.goto('/station/procedures/create?blank=1')

        await page.locator('select').first().selectOption({label: 'Einführung neues Mitglied'})

        const firstStep = page.getByPlaceholder('Titel').first()
        await expect(firstStep).toBeVisible()
        const stepTitle = await firstStep.inputValue()
        expect(stepTitle).not.toBe('')

        await page.getByRole('textbox').first().fill(procedure)
        await chooseAbout(page)
        await page.getByRole('button', {name: 'Neuer Ablauf'}).last().click()

        await page.waitForURL(/\/station\/procedures\/\d+$/)
        await expect(page.getByText(stepTitle).first()).toBeVisible()
    })

    /**
     * Starting from a template asks only whom the procedure is about and when it is due: the template
     * brings the steps, and the procedure is named after the template and the person.
     */
    test('a procedure is started from a template', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        await page.goto('/station/procedures/create')

        await page.locator('[data-testid^="procedure-start-template-"]')
            .filter({hasText: 'Einführung neues Mitglied'}).first().click()
        const dialog = page.getByTestId('procedure-from-template')
        await expect(dialog).toBeVisible()
        await dialog.getByTestId('procedure-from-template-about').getByTestId('member-select-trigger').click()
        await page.keyboard.press('Enter')
        await expect(dialog.getByTestId('procedure-from-template-name'), 'the name follows the person')
            .toContainText('Einführung neues Mitglied: ')
        await dialog.getByTestId('procedure-from-template-submit').click()

        await page.waitForURL(/\/station\/procedures\/\d+$/)
        await expect(page.getByTestId('procedure-about'), 'the page names whom it is about').toBeVisible()
        const procedureId = page.url().match(/procedures\/(\d+)/)?.[1]
        const detail = await (await page.request.get(`/api/v1/procedures/${procedureId}`, {headers})).json()
        expect(detail.items.length, 'the template brought its steps along').toBeGreaterThan(0)
        expect(detail.procedure.name).toContain('Einführung neues Mitglied: ')

        await page.request.delete(`/api/v1/procedures/${procedureId}`, {headers})
    })

    test('the procedure templates are reachable', async ({managerPage: page}) => {
        await page.goto('/station/procedures/templates')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * The preparation for an appointment starts from who is coming to it.
     *
     * <p>The sign-ups are arranged through the endpoints, because the story is about what the menu
     * in the sign-ups tab does and not about three people clicking a button. What it asserts: every
     * person who holds a place gets a procedure of their own, about them and named after them, the steps
     * came from a template, and pressing the same entry again offers what is there instead of building a
     * second set beside it. A procedure without a template has no steps, so the dialog asks for a
     * template first.
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
        await page.getByRole('tab', {name: 'Anmeldungen'}).click()

        await page.getByRole('button', {name: 'Aus den Anmeldungen'}).click()
        await page.getByTestId('signup-procedure-entry').click()

        const template = page.getByTestId('signup-procedure-template')
        await expect(template).toBeVisible()
        await template.selectOption({label: 'Einführung neues Mitglied'})
        await expect(page.getByTestId('signup-procedure-name'), 'the appointment names the list')
            .toHaveValue(new RegExp(appointment))

        await page.getByTestId('signup-procedure-submit').click()
        await page.waitForURL(/\/station\/procedures$/)

        const date = new Date(Date.now() + 27 * 86400000).toISOString().slice(0, 10)
        const prepared = await page.request.get(`/api/v1/procedures/for-event/${eventId}?date=${date}`, {headers})
        expect(prepared.ok(), `the procedures read back (${await prepared.text()})`).toBeTruthy()
        const made: {id: number; name: string; aboutMemberId: number; isPublic: boolean}[] = await prepared.json()
        expect(made.length, 'one procedure for each person coming').toBe(2)
        expect(made.map(procedure => procedure.aboutMemberId).sort(), 'each is about one of them')
            .toEqual(people.map(person => person.id).sort())
        for (const person of people) {
            expect(made.some(procedure => procedure.name.includes(person.name)), `one is named after ${person.name}`)
                .toBe(true)
        }

        const procedureId = made[0]!.id
        const detail = await page.request.get(`/api/v1/procedures/${procedureId}`, {headers})
        expect(detail.ok(), `a procedure reads back (${await detail.text()})`).toBeTruthy()
        const body = await detail.json()
        expect(body.items.length, 'the template brought its steps along').toBeGreaterThan(0)
        expect(body.procedure.isPublic, 'the person it is about reads it').toBe(true)

        await page.goto(`/station/procedures/${procedureId}`)
        await expect(page.getByTestId('procedure-about'), 'the page names whom it is about').toBeVisible()
        await expect(page.getByTestId('procedure-appointment-link'), 'and it leads back to the appointment')
            .toBeVisible()

        await page.goto(`/station/events/${eventId}`)
        await page.getByRole('tab', {name: 'Anmeldungen'}).click()
        await page.getByRole('button', {name: 'Aus den Anmeldungen'}).click()
        await page.getByTestId('signup-procedure-entry').click()

        await expect(page.getByTestId('signup-procedure-existing'),
            'a second press offers what is already there').toBeVisible()

        for (const procedure of made) await page.request.delete(`/api/v1/procedures/${procedure.id}`, {headers})
        await page.request.delete(`/api/v1/events/${eventId}`, {headers})
    })
})
