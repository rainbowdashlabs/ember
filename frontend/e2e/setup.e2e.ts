/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/**
 * The assistant a fresh station is led through.
 *
 * Every seeded station is past it, and a station that is past it is sent away from the assistant
 * rather than shown it again — so what these stories hold is that being sent away happens, for the
 * manager and for the member alike. Walking the assistant itself belongs with a station whose setup
 * is unfinished, which the suite has no way to make yet.
 */
test.describe('Setup assistant', () => {
    test('a station past its setup is not led through the assistant again', async ({managerPage: page}) => {
        await page.goto('/station/setup')

        await expect(page).toHaveURL(/\/station\/dashboard\/overview/)
    })

    test('the steps of the assistant send a finished station away too', async ({managerPage: page}) => {
        for (const step of ['/station/setup/address', '/station/setup/modules', '/station/setup/groups']) {
            await page.goto(step)
            await expect(page).toHaveURL(/\/station\/dashboard\/overview/)
        }
    })

    test('a member is led through no assistant', async ({memberPage: page}) => {
        await page.goto('/station/setup')

        await expect(page.getByRole('button', {name: /Weiter|Speichern/})).toHaveCount(0)
    })
})
