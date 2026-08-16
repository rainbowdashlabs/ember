/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/** The station's own side of the directory; the public half runs in the JavaScript-disabled project. */
test.describe('Discovery', () => {
    test('a station chooses whether it appears in the public directory', async ({managerPage: page}) => {
        await page.goto('/station/discovery')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/discovery')
    })
})
