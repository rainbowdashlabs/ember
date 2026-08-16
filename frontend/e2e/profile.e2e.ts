/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

test.describe('Profile', () => {
    test('a member sees their own profile', async ({memberPage: page}) => {
        await page.goto('/station/profile')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/profile')
    })

    test('a member sees the equipment they hold on their profile', async ({memberPage: page}) => {
        await page.goto('/station/profile/inventory')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
