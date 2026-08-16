/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/** The reach FRM-1 to FRM-3 build on. */
test.describe('Forms', () => {
    test('the form list is reachable', async ({managerPage: page}) => {
        await page.goto('/station/forms')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/forms')
    })

    test('a member reaches the forms they may fill', async ({memberPage: page}) => {
        await page.goto('/station/forms')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
