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

    test('a member reaches the forms they may fill', async ({memberPage: page}) => {
        await page.goto('/station/forms')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
