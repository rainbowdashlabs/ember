/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * The document store. A document is a file kept for the members it concerns, so every story here
 * puts one in and then looks for it where somebody would go for it.
 */
test.describe('Documents', () => {

    /**
     * The store of the whole station, which is where a document that concerns nobody in particular
     * belongs. It is put in with a title, because a file name is not what anybody calls a document.
     */
    test('a document is put in the store and found by its title', async ({managerPage: page}) => {
        const title = unique('Vertrag')

        await page.goto('/station/members/documents')
        await page.getByRole('button', {name: 'Hochladen'}).first().click()

        const dialog = page.getByRole('dialog')
        await dialog.locator('input[type="file"]').setInputFiles({
            name: 'vertrag.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from('Diese Vereinbarung gilt ab sofort.'),
        })
        await dialog.getByPlaceholder('Wie das Dokument heißen soll').fill(title)
        await dialog.getByRole('button', {name: 'Hochladen'}).click()

        await expect(page.getByText(title).first()).toBeVisible()

        await page.getByPlaceholder('Titel oder Inhalt').fill(title)
        await expect(page.getByTestId('document-tile').first()).toBeVisible()
        await expect(page.getByText(title).first()).toBeVisible()
    })

    /**
     * What can be read out of a document is searched too, which is the point of keeping the text:
     * nobody remembers what they called a file, but they remember what was in it.
     */
    test('a document is found by what it says rather than by its name', async ({managerPage: page}) => {
        const word = unique('Loeschzug').replace(/-/g, '')
        const title = unique('Protokoll')

        await page.goto('/station/members/documents')
        await page.getByRole('button', {name: 'Hochladen'}).first().click()

        const dialog = page.getByRole('dialog')
        await dialog.locator('input[type="file"]').setInputFiles({
            name: 'protokoll.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from(`Anwesend war der ${word} in voller Staerke.`),
        })
        await dialog.getByPlaceholder('Wie das Dokument heißen soll').fill(title)
        await dialog.getByRole('button', {name: 'Hochladen'}).click()

        await expect(page.getByText(title).first()).toBeVisible()

        await page.getByPlaceholder('Titel oder Inhalt').fill(word)
        await expect(page.getByText(title).first()).toBeVisible()
    })

    /**
     * A document on a member is what the profile tab is for. The story puts one there and opens it,
     * which is also the only way to see it rather than download it.
     */
    test('a document on a member is opened from their profile', async ({managerPage: page}) => {
        const title = unique('Einverstaendnis')

        await page.goto('/station/members/list')
        await page.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
        await page.waitForURL(/\/station\/members\/detail\/\d+/)

        await page.getByRole('button', {name: 'Dokumente'}).first().click()
        await page.getByRole('button', {name: 'Hochladen'}).first().click()

        const dialog = page.getByRole('dialog')
        await dialog.locator('input[type="file"]').setInputFiles({
            name: 'einverstaendnis.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from('Hiermit erteile ich mein Einverstaendnis.'),
        })
        await dialog.getByPlaceholder('Wie das Dokument heißen soll').fill(title)
        await dialog.getByRole('button', {name: 'Hochladen'}).click()

        await expect(page.getByText(title).first()).toBeVisible()

        await page.getByTestId('document-tile').first().click()
        await expect(page.getByRole('dialog').getByText('Hiermit erteile ich mein Einverstaendnis.')).toBeVisible()
    })

    /**
     * Reading one's own documents needs no permission, and a member who was never granted the
     * upload right is offered no way in.
     */
    test('a member sees their own documents without being offered to add any', async ({memberPage: page}) => {
        await page.goto('/station/profile')

        await expect(page.getByText('Dokumente').first()).toBeVisible()
        await expect(page.getByRole('button', {name: 'Hochladen'})).toHaveCount(0)
    })
})
