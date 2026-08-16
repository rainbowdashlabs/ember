/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

test.describe('Protocols', () => {
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
