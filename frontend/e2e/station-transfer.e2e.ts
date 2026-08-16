/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/**
 * Moving a station to another host. The transfer itself needs two instances, which the compose
 * profile for it provides; these stories hold the page that starts it reachable for whoever runs
 * the station and closed to everyone else.
 */
test.describe('Station transfer', () => {
    test('the transfer page is reachable', async ({managerPage: page}) => {
        await page.goto('/station/moved')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/moved')
    })

    test('a member does not move the station', async ({memberPage: page}) => {
        await page.goto('/station/moved')

        await expect(page.getByRole('button', {name: /Übertragen|Starten/})).toHaveCount(0)
    })
})
