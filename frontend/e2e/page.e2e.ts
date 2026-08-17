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

    /**
     * A public page can carry a poll, and a stranger can answer it. The story answers as nobody at
     * all — no session, the way a visitor arrives — and the page thanks them for it, which is what
     * says the answer was taken.
     */
    test('a poll on a public page takes an answer from a stranger', async ({page}) => {
        await page.goto('/public/station/jugendfeuerwehr-musterstadt/page/komponenten-schaukasten')

        // The page asks the server what the station is, and under a full suite that call sometimes
        // comes back short; it says so and offers another go, which is what a reader would press.
        const retry = page.getByRole('button', {name: 'Erneut versuchen'})
        if (await retry.count() > 0) await retry.click()

        // The choices of a poll are rows to click rather than radio buttons, and answering as a
        // stranger also means agreeing to what is done with the answer.
        const submit = page.getByRole('button', {name: 'Absenden'}).first()
        await expect(submit).toBeVisible()

        await page.getByText('Filmabend').first().click()
        await page.getByRole('checkbox').first().check()
        await submit.click()

        await expect(page.getByText(/Deine Antwort wurde übermittelt|bereits ausgefüllt/)).toBeVisible()
    })

    test('the files behind the public pages are reachable', async ({managerPage: page}) => {
        await page.goto('/station/pages/files')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
