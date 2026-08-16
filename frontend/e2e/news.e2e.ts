/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

test.describe('News', () => {
    test('an article is written and appears in the list', async ({managerPage: page}) => {
        const article = unique('Neuigkeit')

        await page.goto('/station/news')
        await page.getByRole('button', {name: 'Neuigkeit erstellen'}).click()
        await page.waitForURL(/\/station\/news\/create/)

        await page.getByPlaceholder('Titel der Neuigkeit').fill(article)
        const body = page.locator('[contenteditable="true"]').first()
        await body.click()
        await page.keyboard.type('Von der Story geschrieben.')

        await page.getByRole('button', {name: /Speichern|Veröffentlichen|Erstellen/}).last().click()

        await page.goto('/station/news')
        await expect(page.getByText(article).first()).toBeVisible()
    })

    test('a member reads the news of their station', async ({memberPage: page}) => {
        await page.goto('/station/news')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
