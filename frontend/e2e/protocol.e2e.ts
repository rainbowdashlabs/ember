/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/** The dialog's submit repeats the label of the button that opened it, as several others do. */
test.describe('Protocols', () => {
    test('a test sheet is created', async ({managerPage: page}) => {
        const sheet = unique('Pruefungsbogen')

        await page.goto('/station/protocols')
        await page.getByRole('button', {name: 'Neuer Prüfungsbogen'}).click()

        await page.getByRole('textbox').first().fill(sheet)
        await page.getByRole('button', {name: 'Neuer Prüfungsbogen'}).last().click()

        await expect(page.getByText(sheet).first()).toBeVisible()
    })

    test('the protocol list offers a new test sheet to whoever runs them', async ({managerPage: page}) => {
        await page.goto('/station/protocols')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neuer Prüfungsbogen'})).toBeVisible()
    })

    test('a member is not offered a new test sheet', async ({memberPage: page}) => {
        await page.goto('/station/protocols')

        await expect(page.getByRole('button', {name: 'Neuer Prüfungsbogen'})).toHaveCount(0)
    })
})
