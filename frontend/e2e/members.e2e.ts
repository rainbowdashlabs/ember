/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/**
 * The creation wizard walks five steps and writes an account. It is written once a run can be
 * relied on to clear up after itself, so that the seeded station does not fill with test members.
 */
test.describe('Members', () => {
    test('the member list shows the station and filters by name', async ({managerPage: page}) => {
        await page.goto('/station/members/list')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        const rows = page.getByRole('row')
        await expect(rows.first()).toBeVisible()

        const before = await rows.count()
        await page.getByPlaceholder(/Suche/).first().fill('zzzz-kein-treffer')
        await expect(async () => {
            expect(await rows.count()).toBeLessThan(before)
        }).toPass()
    })

    /**
     * A permission is not a hidden button: the page has to be unreachable for someone without it,
     * which is what stops a guessed URL from working.
     */
    test('a member without the right cannot open the member list', async ({memberPage: page}) => {
        await page.goto('/station/members/list')

        await expect(page.getByRole('table')).toHaveCount(0)
    })
})
