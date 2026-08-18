/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

test.describe('Dashboard', () => {
    test('the dashboard shows a member their day', async ({memberPage: page}) => {
        await page.goto('/station/dashboard/overview')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('link', {name: /Profil/}).first()).toBeVisible()
    })

    test('the station statistics render', async ({managerPage: page}) => {
        await page.goto('/station/dashboard/statistics')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText('Statistiken').first()).toBeVisible()
    })
})
