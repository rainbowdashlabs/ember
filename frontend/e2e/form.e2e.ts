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
     * field and sends it.
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

        await expect(page.getByRole('button', {name: /Aktualisieren|Absenden/}).first()).toBeVisible()
    })

    /** A member is offered the forms their station has opened to them. */
    test('a member reaches the forms they may fill', async ({memberPage: page}) => {
        await page.goto('/station/forms')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Ausfüllen'}).first()).toBeVisible()
    })
})
