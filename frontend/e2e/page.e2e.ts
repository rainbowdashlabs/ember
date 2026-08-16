/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

test.describe('Pages', () => {
    test('a page is created', async ({managerPage: page}) => {
        const title = unique('Seite')

        await page.goto('/station/pages')
        await page.getByRole('button', {name: 'Seite erstellen'}).click()

        await page.getByRole('textbox').first().fill(title)
        await page.getByRole('button', {name: /Speichern|Erstellen|Anlegen/}).last().click()

        await expect(page.getByText(title).first()).toBeVisible()
    })

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
