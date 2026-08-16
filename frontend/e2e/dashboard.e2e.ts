/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/** DSH-1 and DSH-2 of the story list. */
test.describe('Dashboard', () => {
    test('DSH-1 the dashboard shows the member their day', async ({memberPage: page}) => {
        await page.goto('/station/dashboard/overview')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('link', {name: /Profil/}).first()).toBeVisible()
    })

    test('DSH-2 station statistics render', async ({managerPage: page}) => {
        await page.goto('/station/dashboard/statistics')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText('Statistiken').first()).toBeVisible()
    })
})
