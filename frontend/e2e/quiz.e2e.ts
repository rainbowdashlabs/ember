/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/** Both halves of the quiz are open to whoever configures them. */
test.describe('Quiz', () => {
    test('a question catalogue is created', async ({managerPage: page}) => {
        const catalogue = unique('Katalog')

        await page.goto('/station/quiz/catalogs')
        await page.getByRole('button', {name: 'Neuer Katalog'}).click()

        await page.getByRole('textbox').first().fill(catalogue)
        await page.getByRole('button', {name: /Speichern|Erstellen|Anlegen/}).last().click()

        await expect(page.getByText(catalogue).first()).toBeVisible()
    })

    /** A catalogue opens on its categories, which is where its questions live. */
    test('a created catalogue opens', async ({managerPage: page}) => {
        const catalogue = unique('Katalog')

        await page.goto('/station/quiz/catalogs')
        await page.getByRole('button', {name: 'Neuer Katalog'}).click()
        await page.getByRole('textbox').first().fill(catalogue)
        await page.getByRole('button', {name: /Speichern|Erstellen|Anlegen/}).last().click()
        await expect(page.getByText(catalogue).first()).toBeVisible()

        await page.getByText(catalogue).first().click()

        await page.waitForURL(/\/station\/quiz\/catalogs\/\d+/)
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('the test sheets are reachable', async ({managerPage: page}) => {
        await page.goto('/station/quiz/tests')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/quiz/tests')
    })

    /** Training asks questions without recording an attempt, so it is open to everyone. */
    test('a member reaches the training', async ({memberPage: page}) => {
        await page.goto('/station/quiz/training')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
