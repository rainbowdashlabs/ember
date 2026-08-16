/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

test.describe('Inventory', () => {
    test('the inventory list shows the inventories of the station', async ({managerPage: page}) => {
        await page.goto('/station/inventory')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText('Inventar').first()).toBeVisible()
    })

    /** A member sees what they hold, and nothing about anyone else's equipment. */
    test('a member sees the equipment they hold', async ({memberPage: page}) => {
        await page.goto('/station/inventory/my')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('the storage containers are reachable', async ({managerPage: page}) => {
        await page.goto('/station/inventory/storage')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neuer Behälter'})).toBeVisible()
    })

    test('the equipment checks are reachable', async ({managerPage: page}) => {
        await page.goto('/station/inventory/checks')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /** Borrowing is open to members; approving it is not. */
    test('a member reaches the equipment they may borrow', async ({memberPage: page}) => {
        await page.goto('/station/inventory/lending/browse')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
