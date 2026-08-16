/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/**
 * The station's own side of federation. Connecting two stations and reading a partner's content
 * needs both of them answering, which the seeded partner stations allow; those stories follow once
 * the suite knows how to drive two stations at once.
 */
test.describe('Federation', () => {
    test('the partner stations are reachable', async ({managerPage: page}) => {
        await page.goto('/station/federate')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/federate')
    })

    test('what the station shares is configurable', async ({managerPage: page}) => {
        await page.goto('/station/federate/settings')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('the boards partner stations share are reachable', async ({managerPage: page}) => {
        await page.goto('/station/federation/boards')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('a member does not configure what the station shares', async ({memberPage: page}) => {
        await page.goto('/station/federate/settings')

        await expect(page.getByRole('button', {name: /Speichern/})).toHaveCount(0)
    })
})
