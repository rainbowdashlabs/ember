/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

test.describe('Two-factor', () => {
    test('a member reaches their own security settings', async ({memberPage: page}) => {
        await page.goto('/account/security')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/account/security')
    })

    test('a member reaches the sessions they hold', async ({memberPage: page}) => {
        await page.goto('/account/sessions')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
