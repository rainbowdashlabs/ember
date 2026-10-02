/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'
import {unique} from './fixtures/unique'

test.describe('Forms', () => {
    test('a form is created', async ({managerPage: page}) => {
        const form = unique('Umfrage')

        await page.goto('/station/forms')
        await page.getByRole('button', {name: 'Umfrage erstellen'}).click()

        await page.getByRole('textbox').first().fill(form)
        await page.getByRole('button', {name: /Speichern|Erstellen|Weiter/}).last().click()

        await expect(page.getByText(form).first()).toBeVisible()
    })

    /**
     * A form exists to be answered. The member opens one they are offered, fills it in and sends
     * it. Sending leaves them on a screen that says the answer went through, with the form's own
     * words where it has any, and from there they go back to the list.
     */
    test('a member fills in a form and sends it', async ({memberPage: page}) => {
        await page.goto('/station/forms')
        await openOfferedForm(page)

        await answerRequired(page, unique('Antwort'))
        await page.getByRole('button', {name: 'Absenden'}).click()

        await expect(page.getByTestId('form-sent')).toBeVisible()
        await page.getByRole('button', {name: 'Zurück zu den Umfragen'}).click()
        await page.waitForURL(/\/station\/forms$/)
        await expect(page.getByRole('button', {name: /Ausfüllen|Antwort bearbeiten/}).first()).toBeVisible()
    })

    /**
     * The point of asking is reading the answers. The story opens the evaluation of a form that has
     * been answered and reads one answer in full - which is a different page from the totals, and
     * the one somebody goes to when they want to know what a particular person wrote. The manager
     * answers the form first, so the story does not depend on whoever ran before.
     */
    test('the answers to a form are read by whoever owns it', async ({managerPage: page}) => {
        await page.goto('/station/forms')
        const id = await openOfferedForm(page)

        await answerRequired(page, unique('Antwort'))
        await page.getByRole('button', {name: /Absenden|Aktualisieren/}).click()
        await expect(page.getByTestId('form-sent')).toBeVisible()

        await page.goto(`/station/forms/${id}/analytics`)

        await expect(page.getByText(/Antworten gesamt: [1-9]/), 'the total counts the answer just given').toBeVisible()

        await page.getByRole('tab', {name: 'Einzelantworten'}).click()
        await expect(page.getByText(/\d+ von \d+/)).toBeVisible()
    })

    /**
     * The results of an internal survey can be split by who answered. The manager opens one that has
     * answers, groups its results by member type and switches to the table view, where the group of a
     * type that answered stands as a column: the chart itself is drawn on a canvas and carries no text
     * to read.
     */
    test('the results are grouped by who answered', async ({managerPage: page}) => {
        const id = await answeredInternalForm(page)

        await page.goto(`/station/forms/${id}/analytics`)
        await page.locator('select').filter({has: page.locator('option', {hasText: 'Nicht gruppieren'})})
            .selectOption('USER_TYPE')
        await page.getByRole('button', {name: 'Als Tabelle'}).click()

        await expect(page.locator('thead').getByText(/Probe|Mitglied|Erziehungsberechtigter|Team|Manager|Ohne Anmeldung/).first())
            .toBeVisible()
        await expect(page).toHaveURL(/view=/)
    })

    /** Grouping the results is reading them, which an ordinary member may not do. */
    test('an ordinary member may not group the results', async ({memberPage: page}) => {
        const headers = await apiHeaders(page)
        const forms = await (await page.request.get('/api/v1/forms/available', {headers})).json()
        const id = forms[0]?.id ?? 1

        const response = await page.request.post(`/api/v1/forms/${id}/analytics/query`, {
            headers,
            data: {filter: null, groupBy: {by: 'USER_TYPE', only: [], bounds: []}},
        })

        expect(response.status(), await response.text()).toBe(403)
    })

    /** A member is offered the forms their station has opened to them. */
    test('a member reaches the forms they may fill', async ({memberPage: page}) => {
        await page.goto('/station/forms')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Ausfüllen'}).first()).toBeVisible()
    })
})

test.describe('Forms with pages', () => {
    /**
     * A form can be split into pages, and one answer can decide which page comes next. The manager
     * builds one through the API, answers "Nein" and is led past the page for those who come, to
     * the page asking why not, and from there to sending.
     */
    test('an answer leads to the page it is set to lead to', async ({managerPage: page}) => {
        const id = await pagedForm(page)

        await page.goto(`/station/forms/${id}/fill`)
        await expect(page.getByTestId('form-page-intro')).toContainText('Seite 1')
        await page.getByTestId('choice-option').filter({hasText: 'Nein'}).click()
        await page.getByTestId('form-page-next').click()

        await expect(page.getByText('Warum nicht?')).toBeVisible()
        await expect(page.getByText('Was bringst du mit?')).toHaveCount(0)
        await page.getByRole('textbox').first().fill(unique('Urlaub'))
        await page.getByTestId('form-send').click()

        await expect(page.getByTestId('form-sent')).toBeVisible()
    })

    /**
     * A required question on the page the reader is on stops them going on, and says so at the
     * question rather than somewhere above the form.
     */
    test('a required question is marked before the page can be left', async ({managerPage: page}) => {
        const id = await pagedForm(page)

        await page.goto(`/station/forms/${id}/fill`)
        await page.getByTestId('choice-option').filter({hasText: 'Ja'}).click()
        await page.getByTestId('form-page-next').click()
        await page.getByTestId('form-send').click()

        await expect(page.getByTestId('question-error')).toBeVisible()
        await expect(page.getByTestId('form-sent')).toHaveCount(0)
    })

    /**
     * A long form is not always finished in one sitting. Going on to the next page keeps what was
     * filled in, and opening the form again continues on that page, until the reader starts over.
     */
    test('a half-filled form continues where it was left', async ({managerPage: page}) => {
        const id = await pagedForm(page)

        await page.goto(`/station/forms/${id}/fill`)
        await page.getByTestId('choice-option').filter({hasText: 'Ja'}).click()
        await page.getByTestId('form-page-next').click()
        await expect(page.getByText('Was bringst du mit?')).toBeVisible()

        await expect.poll(async () => {
            const headers = await apiHeaders(page)
            return (await (await page.request.get(`/api/v1/forms/${id}/draft`, {headers})).json()).draft?.path
        }).toEqual(['start', 'yes'])

        await page.goto('/station/forms')
        await page.goto(`/station/forms/${id}/fill`)
        await expect(page.getByTestId('form-draft-note')).toBeVisible()
        await expect(page.getByText('Was bringst du mit?')).toBeVisible()

        await page.getByTestId('form-draft-start-over').click()
        await expect(page.getByTestId('form-draft-note')).toHaveCount(0)
        await expect(page.getByText('Kommst du mit?')).toBeVisible()
    })

    /**
     * The editor's preview walks the pages the way the form will be filled in, shows the path taken
     * so far, and sends nothing.
     */
    test('the preview walks the pages and sends nothing', async ({managerPage: page}) => {
        const id = await pagedForm(page)

        await page.goto(`/station/forms/${id}/edit`)
        await page.getByTestId('form-preview-toggle').click()
        const preview = page.getByTestId('form-preview')
        await preview.getByTestId('choice-option').filter({hasText: 'Nein'}).first().click()
        await preview.getByTestId('form-page-next').click()
        await expect(preview.getByTestId('preview-path')).toContainText('Warum')
        await preview.getByTestId('form-send').click()

        await expect(preview.getByText('In der Vorschau wird nichts gespeichert.')).toBeVisible()
        const headers = await apiHeaders(page)
        const form = await (await page.request.get(`/api/v1/forms/${id}`, {headers})).json()
        expect(form.responseCount).toBe(0)
    })
})

/** What the forms made by the stories about pages are called, so the other stories can pass them by. */
const PAGED_FORM = 'Ausflug'

/**
 * Opens the first form the forms page offers to be filled in, passing by the forms the stories about
 * pages make while this one runs, and returns its id.
 */
async function openOfferedForm(page: Page): Promise<string | undefined> {
    await page.getByTestId('available-form').filter({hasNotText: PAGED_FORM}).getByRole('button', {name: 'Ausfüllen'})
        .first().click()
    await page.waitForURL(/\/station\/forms\/(\d+)\/fill/)
    return page.url().match(/forms\/(\d+)/)?.[1]
}

/**
 * Writes the answer into every text field of the open form and picks the first option where it asks
 * for a choice. A form refuses to be sent while a required question is unanswered, and the forms a
 * story is offered first, such as the seeded contact form, ask for more than one field.
 */
async function answerRequired(page: Page, answer: string) {
    const fields = page.getByRole('textbox')
    await expect(fields.first()).toBeVisible()
    for (const field of await fields.all()) await field.fill(answer)

    const options = page.getByTestId('choice-option')
    if (await options.count() > 0) await options.first().click()
}

/**
 * An open internal form of three pages, made through the API: whether the reader comes decides
 * between a page for those who come and one for those who do not, and both send the form after.
 *
 * <p>It is put to the manager making it and to nobody else, so no other story finds it among the
 * forms it is offered.
 */
async function pagedForm(page: Page): Promise<number> {
    const headers = await apiHeaders(page)
    const made = await (await page.request.post('/api/v1/forms', {
        headers,
        data: {title: unique(PAGED_FORM), purpose: 'INTERNAL'},
    })).json() as {id: number}
    const me = await (await page.request.get('/api/v1/session', {headers})).json() as {member: {id: number}}
    await page.request.put(`/api/v1/forms/${made.id}/restrictions`, {
        headers,
        data: {userTypes: [], groupIds: [], tagIds: [], memberIds: [me.member.id]},
    })
    const choice = {
        questionType: 'CHOICE',
        options: [{key: 'yes', label: 'Ja'}, {key: 'no', label: 'Nein'}],
        multiSelect: false,
    }
    const saved = await page.request.put(`/api/v1/forms/${made.id}/questions`, {
        headers,
        data: {
            pages: [
                {key: 'start', title: '', description: '', after: {kind: 'NEXT'}},
                {key: 'yes', title: 'Mitfahrt', description: '', after: {kind: 'SUBMIT'}},
                {key: 'no', title: 'Warum', description: '', after: {kind: 'NEXT'}},
            ],
            questions: [
                {pageKey: 'start', questionType: 'CHOICE', title: 'Kommst du mit?', required: true, config: choice,
                    branch: {yes: {kind: 'PAGE', page: 'yes'}, no: {kind: 'PAGE', page: 'no'}}},
                {pageKey: 'yes', questionType: 'TEXT', title: 'Was bringst du mit?', required: true,
                    config: {questionType: 'TEXT', longAnswer: false}},
                {pageKey: 'no', questionType: 'TEXT', title: 'Warum nicht?', required: false,
                    config: {questionType: 'TEXT', longAnswer: false}},
            ],
        },
    })
    expect(saved.ok(), await saved.text()).toBeTruthy()
    await page.request.post(`/api/v1/forms/${made.id}/publish`, {headers})
    return made.id
}

/**
 * An internal survey of the station that already has answers, found through the API.
 *
 * <p>Only an internal survey can be grouped by who answered, and the forms page lists contact forms
 * among the internal ones, so the first form it offers is not necessarily one. The seeded surveys
 * come with answers, which is why none is given here.
 */
async function answeredInternalForm(page: Page): Promise<number> {
    const headers = await apiHeaders(page)
    const forms = await (await page.request.get('/api/v1/forms?purpose=INTERNAL', {headers})).json() as {
        id: number
        responseCount?: number
    }[]
    const answered = forms.find(form => (form.responseCount ?? 0) > 0)
    expect(answered, 'the seeded station has an answered internal survey').toBeTruthy()
    return answered!.id
}
