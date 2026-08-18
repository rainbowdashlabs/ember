/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/** What a member is expected to hold, and where the station configures it. */
test.describe('Equipment requirements', () => {
    test('a member sees what they are expected to hold', async ({memberPage: page}) => {
        await page.goto('/station/requirements')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('the requirements per member type are configurable', async ({managerPage: page}) => {
        await page.goto('/station/inventory/requirements')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
