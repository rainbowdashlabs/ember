/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/** NWS-1 and the reading half of the news stories. */
test.describe('News', () => {
    test('the news list offers writing an article', async ({managerPage: page}) => {
        await page.goto('/station/news')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neuigkeit erstellen'})).toBeVisible()
    })

    test('a member reads the news of their station', async ({memberPage: page}) => {
        await page.goto('/station/news')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
