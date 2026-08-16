/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

test.describe('Lost and found', () => {
    test('the lost and found takes a report', async ({managerPage: page}) => {
        await page.goto('/station/lost-and-found')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Fundgegenstand melden'})).toBeVisible()
    })
})
