/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

test.describe('Pages', () => {
    test('the public pages of the station are reachable', async ({managerPage: page}) => {
        await page.goto('/station/pages')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/pages')
    })

    test('the files behind the public pages are reachable', async ({managerPage: page}) => {
        await page.goto('/station/pages/files')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
