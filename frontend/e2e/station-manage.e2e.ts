/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

test.describe('Station management', () => {
    test('the station settings are reachable', async ({managerPage: page}) => {
        await page.goto('/station/manage')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/manage')
    })

    test('the modules of the station are reachable', async ({managerPage: page}) => {
        await page.goto('/station/manage/modules')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('a member reaches none of the station settings', async ({memberPage: page}) => {
        for (const path of ['/station/manage', '/station/manage/modules', '/station/manage/security']) {
            await page.goto(path)
            await expect(page.getByRole('button', {name: /Speichern/})).toHaveCount(0)
        }
    })
})
