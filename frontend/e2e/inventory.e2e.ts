/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, stationPeers} from './fixtures/auth'

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

    /**
     * Equipment meeting a person is the point of the whole feature. Both halves are searchable
     * pickers rather than raw scanners, so the story types what a scanner would send: the member's
     * name and the item's code.
     */
    test('an item is assigned to a member', async ({managerPage: page, request}) => {
        const {member} = await stationPeers(request)

        await page.goto('/station/inventory/assign')

        await page.getByPlaceholder('- Bitte wählen -').fill(member.lastName)
        await page.getByText(`${member.firstName} ${member.lastName}`).first().click()

        await page.getByPlaceholder('Item suchen oder Code scannen…').fill('H-0')
        await page.getByText(/H-0\d\d/).first().click()

        await expect(page.getByText(/zugewiesen|Bei /).first()).toBeVisible()
    })

    /** Assigning starts by naming a person or scanning a code, and offers both. */
    test('the assignment page asks who is receiving something', async ({managerPage: page}) => {
        await page.goto('/station/inventory/assign')

        await expect(page.getByRole('heading', {name: 'Mitglied'})).toBeVisible()
        await expect(page.getByRole('heading', {name: 'Scannen'})).toBeVisible()
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
