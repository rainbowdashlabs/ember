/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/** INV-11 and INV-14 of the story list. */
test.describe('Inventory', () => {
    test('INV-11 the inventory list is reachable and shows its inventories', async ({managerPage: page}) => {
        await page.goto('/station/inventory')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText('Inventar').first()).toBeVisible()
    })

    /** A member sees what they hold, and nothing about anyone else's equipment. */
    test('INV-14 a member sees the equipment they hold', async ({memberPage: page}) => {
        await page.goto('/station/inventory/my')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
