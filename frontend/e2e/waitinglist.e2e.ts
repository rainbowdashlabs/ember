/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

test.describe('Waiting lists', () => {
    test('the waiting lists of the station are reachable', async ({managerPage: page}) => {
        await page.goto('/station/members/waiting-lists')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/members/waiting-lists')
    })

    test('a member does not reach the waiting lists', async ({memberPage: page}) => {
        await page.goto('/station/members/waiting-lists')

        await expect(page.getByRole('table')).toHaveCount(0)
    })
})
