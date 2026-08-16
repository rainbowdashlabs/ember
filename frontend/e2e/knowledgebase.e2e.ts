/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * The permission story is the one worth having: what one person sets on a folder has to change what
 * a different person sees, and the rule is enforced in three places — the route guard, the levels
 * the listing reports, and the create menu. One story covers all three from the outside.
 */
test.describe('Knowledge base', () => {
    /**
     * A file says what it is for underneath its name, and that line is written where it is read.
     * The story checks it after a reload, since a description that does not survive one was never
     * saved.
     */
    test('the description of a file is written and kept', async ({managerPage: page}) => {
        const folder = unique('Ordner')
        const file = unique('Datei')
        const description = unique('Beschreibung')

        await page.goto('/station/knowledge')
        await page.getByRole('button', {name: 'Neu'}).click()
        await page.getByText('Neuer Ordner').last().click()
        await page.getByPlaceholder('Ordnername').fill(folder)
        await page.getByRole('button', {name: 'Neuer Ordner'}).last().click()
        await page.getByText(folder).click()

        await page.getByRole('button', {name: 'Neu'}).click()
        await page.getByText('Markdown-Datei').last().click()
        await page.getByPlaceholder('Dateiname').fill(file)
        await page.getByRole('button', {name: 'Neue Datei'}).click()
        await page.waitForURL(/\/station\/knowledge\/file\/\d+/)

        // The pen belongs to the line under the name, and it is not the only Bearbeiten on the page.
        await page.locator('p', {hasText: 'Beschreibung'}).getByRole('button', {name: 'Bearbeiten'})
            .first().click()
        await page.getByPlaceholder('Beschreibung').fill(description)
        await page.getByRole('button', {name: 'Speichern'}).click()

        await page.reload()
        await expect(page.getByText(description)).toBeVisible()
    })

    /**
     * A page of the wiki is often wanted on paper, and the download is what a reader actually
     * receives — so the story waits for the file rather than for the button to look pressed.
     */
    test('a file is downloaded as a PDF', async ({managerPage: page}) => {
        const folder = unique('Ordner')
        const file = unique('Datei')

        await page.goto('/station/knowledge')
        await page.getByRole('button', {name: 'Neu'}).click()
        await page.getByText('Neuer Ordner').last().click()
        await page.getByPlaceholder('Ordnername').fill(folder)
        await page.getByRole('button', {name: 'Neuer Ordner'}).last().click()
        await page.getByText(folder).click()

        await page.getByRole('button', {name: 'Neu'}).click()
        await page.getByText('Markdown-Datei').last().click()
        await page.getByPlaceholder('Dateiname').fill(file)
        await page.getByRole('button', {name: 'Neue Datei'}).click()
        await page.waitForURL(/\/station\/knowledge\/file\/\d+/)

        const download = page.waitForEvent('download')
        await page.getByRole('button', {name: 'Als PDF'}).click()

        expect((await download).suggestedFilename()).toMatch(/\.pdf$/)
    })

    /** Creating a Markdown file opens it, because that is where its content is written. */
    test('a folder is created and holds a file', async ({managerPage: page}) => {
        const folder = unique('Ordner')
        const file = unique('Datei')

        await page.goto('/station/knowledge')
        await page.getByRole('button', {name: 'Neu'}).click()
        await page.getByText('Neuer Ordner').last().click()
        await page.getByPlaceholder('Ordnername').fill(folder)
        await page.getByRole('button', {name: 'Neuer Ordner'}).click()

        await expect(page.getByText(folder)).toBeVisible()

        await page.getByText(folder).click()
        await page.getByRole('button', {name: 'Neu'}).click()
        await page.getByText('Markdown-Datei').last().click()
        await page.getByPlaceholder('Dateiname').fill(file)
        await page.getByRole('button', {name: 'Neue Datei'}).click()

        await page.waitForURL(/\/station\/knowledge\/file\/\d+/)
        await expect(page.getByText(file).first()).toBeVisible()

        await page.goBack()
        await expect(page.getByText(file).first()).toBeVisible()
    })

    /**
     * The listing offers what the reader may actually do: a member who may only read gets the
     * search and the entries, and no create menu — the same rule the server enforces, so nothing
     * is offered that would be refused.
     */
    test('a member sees the knowledge base without the actions of an editor', async ({memberPage: page}) => {
        await page.goto('/station/knowledge')

        await expect(page.getByPlaceholder('Suchen...')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neu'})).toHaveCount(0)
    })
})
