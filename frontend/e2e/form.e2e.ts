/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

test.describe('Forms', () => {
    test('a form is created', async ({managerPage: page}) => {
        const form = unique('Formular')

        await page.goto('/station/forms')
        await page.getByRole('button', {name: 'Formular erstellen'}).click()

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
        const answer = unique('Antwort')

        // The manager answers a form of the station and then reads that answer back, so the story
        // depends on nothing but itself: a form nobody has answered has nothing to read, and which
        // of the seeded forms carries an answer is up to whoever ran before.
        await page.goto('/station/forms')
        await page.getByRole('button', {name: 'Ausfüllen'}).first().click()
        await page.waitForURL(/\/station\/forms\/(\d+)\/fill/)
        const id = page.url().match(/forms\/(\d+)/)?.[1]

        const field = page.getByRole('textbox').first()
        await expect(field).toBeVisible()
        await field.fill(answer)

        // A form may insist on a choice as well, and it refuses to be sent while one is missing.
        const options = page.getByTestId('choice-option')
        if (await options.count() > 0) await options.first().click()

        await page.getByRole('button', {name: /Absenden|Aktualisieren/}).click()

        await page.goto(`/station/forms/${id}/analytics`)

        // The evaluation counts what came in and offers the answers one by one. The story holds it
        // to both: a total that counts the answer just given, and the page that shows answers in
        // full - which is a different page from the totals, and the one somebody opens when they
        // want to know what a particular person wrote.
        await expect(page.getByText(/Antworten gesamt: [1-9]/)).toBeVisible()

        await page.getByRole('button', {name: 'Einzelantworten'}).click()
        await expect(page.getByText(/\d+ von \d+/)).toBeVisible()
    })

    /** A member is offered the forms their station has opened to them. */
    test('a member reaches the forms they may fill', async ({memberPage: page}) => {
        await page.goto('/station/forms')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Ausfüllen'}).first()).toBeVisible()
    })
})
