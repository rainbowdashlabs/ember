/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/** The account area sits beside the station rather than inside it, and every member has one. */
test.describe('Account settings', () => {
    test('a member reaches their profile', async ({memberPage: page}) => {
        await page.goto('/account')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('a member reaches the export of their own data', async ({memberPage: page}) => {
        await page.goto('/account/gdpr')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('a member reaches the appearance settings', async ({memberPage: page}) => {
        await page.goto('/account/theming')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('notification preferences are reachable', async ({memberPage: page}) => {
        await page.goto('/station/profile/settings/notifications')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
