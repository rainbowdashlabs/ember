/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/**
 * The assistant a fresh station is led through. The seeded stations are past it, so these stories
 * hold its steps reachable for whoever runs a station; walking one to the end belongs with a
 * station created for the purpose.
 */
test.describe('Setup assistant', () => {
    test('the assistant is reachable and resumes where it stands', async ({managerPage: page}) => {
        await page.goto('/station/setup')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/setup')
    })

    test('its steps are reachable on their own', async ({managerPage: page}) => {
        for (const step of ['/station/setup/address', '/station/setup/modules', '/station/setup/groups']) {
            await page.goto(step)
            await expect(page.getByTestId('app-shell')).toBeVisible()
        }
    })

    test('a member is led through no assistant', async ({memberPage: page}) => {
        await page.goto('/station/setup')

        await expect(page.getByRole('button', {name: /Weiter|Speichern/})).toHaveCount(0)
    })
})
