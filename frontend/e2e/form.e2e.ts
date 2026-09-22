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
     * A form exists to be answered. The member opens one they are offered, writes into the first
     * field and sends it. Sending takes them back to the list they came from, which is the only
     * sign the application gives that the answer went through: staying on the form is what happens
     * when it did not.
     */
    test('a member fills in a form and sends it', async ({memberPage: page}) => {
        const answer = unique('Antwort')

        await page.goto('/station/forms')
        await page.getByRole('button', {name: 'Ausfüllen'}).first().click()
        await page.waitForURL(/\/station\/forms\/\d+\/fill/)

        const field = page.getByRole('textbox').first()
        await expect(field).toBeVisible()
        await field.fill(answer)
        await page.getByRole('button', {name: 'Absenden'}).click()

        await page.waitForURL(/\/station\/forms$/)
        await expect(page.getByRole('button', {name: /Ausfüllen|Antwort bearbeiten/}).first()).toBeVisible()
    })

    /**
     * The point of asking is reading the answers. The story opens the evaluation of a form that has
     * been answered and reads one answer in full - which is a different page from the totals, and
     * the one somebody goes to when they want to know what a particular person wrote.
     */
    test('the answers to a form are read by whoever owns it', async ({managerPage: page}) => {
        const id = await answerTheFirstForm(page)

        await page.goto(`/station/forms/${id}/analytics`)

        // The evaluation counts what came in and offers the answers one by one. The story holds it
        // to both: a total that counts the answer just given, and the page that shows answers in
        // full - which is a different page from the totals, and the one somebody opens when they
        // want to know what a particular person wrote.
        await expect(page.getByText(/Antworten gesamt: [1-9]/)).toBeVisible()

        await page.getByRole('button', {name: 'Einzelantworten'}).click()
        await expect(page.getByText(/\d+ von \d+/)).toBeVisible()
    })

    /**
     * The results of an internal form can be split by who answered. The manager answers a form,
     * groups its results by member type and switches to the table view, where the group of their
     * own type stands as a column: the chart itself is drawn on a canvas and carries no text to read.
     */
    test('the results are grouped by who answered', async ({managerPage: page}) => {
        const id = await answerTheFirstForm(page)

        await page.goto(`/station/forms/${id}/analytics`)
        await page.locator('select').filter({has: page.locator('option', {hasText: 'Nicht gruppieren'})})
            .selectOption('USER_TYPE')
        await page.getByRole('button', {name: 'Als Tabelle'}).click()

        await expect(page.locator('thead').getByText(/Probe|Mitglied|Erziehungsberechtigter|Team|Manager/).first())
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

/**
 * Answers the first form the station offers and returns its id.
 *
 * <p>A story that reads results answers a form itself first, so it depends on nothing but itself:
 * a form nobody has answered has nothing to read, and which of the seeded forms carries an answer
 * is up to whoever ran before. A form may insist on a choice as well, and refuses to be sent while
 * one is missing, so the first choice is picked where there is one.
 */
async function answerTheFirstForm(page: Page): Promise<string | undefined> {
    await page.goto('/station/forms')
    await page.getByRole('button', {name: 'Ausfüllen'}).first().click()
    await page.waitForURL(/\/station\/forms\/(\d+)\/fill/)
    const id = page.url().match(/forms\/(\d+)/)?.[1]

    const field = page.getByRole('textbox').first()
    await expect(field).toBeVisible()
    await field.fill(unique('Antwort'))

    const options = page.getByTestId('choice-option')
    if (await options.count() > 0) await options.first().click()

    await page.getByRole('button', {name: /Absenden|Aktualisieren/}).click()
    return id
}
