/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

test.describe('Pages', () => {
    /**
     * A page starts as a draft, which is the whole reason the list distinguishes the two: nothing
     * a manager writes is public until they say so. The story writes one and then says so, and
     * reloads, because a badge that changes only in the open page has published nothing.
     */
    test('a page is created as a draft and published', async ({managerPage: page}) => {
        const title = unique('Seite')

        await page.goto('/station/pages')
        await page.getByRole('button', {name: 'Seite erstellen'}).click()

        await page.getByRole('textbox').first().fill(title)
        await page.getByRole('button', {name: /Speichern|Erstellen|Anlegen/}).last().click()

        const row = page.getByTestId('page-row').filter({hasText: title})
        await expect(row.getByText('Entwurf')).toBeVisible()

        await row.getByRole('button', {name: 'Veröffentlichen'}).click()
        await expect(row.getByText('Veröffentlicht', {exact: true})).toBeVisible()

        await page.reload()
        await expect(row.getByText('Veröffentlicht', {exact: true})).toBeVisible()
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
