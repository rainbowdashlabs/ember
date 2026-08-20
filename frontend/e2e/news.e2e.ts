/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, type Page} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * A file the demo seeder puts in the station library. The stack these stories run against is a
 * public demo, which does not take uploads from whoever walks in, so an attachment is picked out of
 * the library rather than brought along.
 */
const SEEDED_FILE = 'fahrzeug.png'

/**
 * Writes an entry and leaves the browser on its edit page, which is where the stories below start.
 * The list is the only place an entry can be opened from, so the walk goes through it.
 */
async function writeArticle(page: Page, title: string, body: string): Promise<string> {
    await page.goto('/station/news')
    await page.getByRole('button', {name: 'Neuigkeit erstellen'}).click()
    await page.waitForURL(/\/station\/news\/create/)

    await page.getByPlaceholder('Titel der Neuigkeit').fill(title)
    const editor = page.locator('[contenteditable="true"]').first()
    await editor.click()
    await page.keyboard.type(body)
    await page.getByRole('button', {name: /Speichern|Veröffentlichen|Erstellen/}).last().click()

    await page.goto('/station/news')
    await page.getByText(title).first().click()
    await page.waitForURL(/\/station\/news\/\d+/)
    const detailUrl = page.url()
    await page.goto(`${detailUrl}/edit`)
    return detailUrl
}

test.describe('News', () => {
    test('an article is written and appears in the list', async ({managerPage: page}) => {
        const article = unique('Neuigkeit')

        await page.goto('/station/news')
        await page.getByRole('button', {name: 'Neuigkeit erstellen'}).click()
        await page.waitForURL(/\/station\/news\/create/)

        await page.getByPlaceholder('Titel der Neuigkeit').fill(article)
        const body = page.locator('[contenteditable="true"]').first()
        await body.click()
        await page.keyboard.type('Von der Story geschrieben.')

        await page.getByRole('button', {name: /Speichern|Veröffentlichen|Erstellen/}).last().click()

        await page.goto('/station/news')
        await expect(page.getByText(article).first()).toBeVisible()
    })

    /**
     * The article and the public blog are two sides of one act: a station writes something and the
     * world can read it. The story crosses from the station into the public pages, where nobody is
     * logged in at all.
     */
    test('an article marked for the blog appears publicly', async ({managerPage: page}) => {
        const article = unique('Blogbeitrag')

        await page.goto('/station/news')
        await page.getByRole('button', {name: 'Neuigkeit erstellen'}).click()
        await page.waitForURL(/\/station\/news\/create/)

        await page.getByPlaceholder('Titel der Neuigkeit').fill(article)
        const body = page.locator('[contenteditable="true"]').first()
        await body.click()
        await page.keyboard.type('Für alle sichtbar.')
        await page.getByRole('button', {name: /Speichern|Veröffentlichen|Erstellen/}).last().click()

        await page.goto('/station/news')
        await page.getByText(article).first().click()
        await page.waitForURL(/\/station\/news\/\d+/)

        const editUrl = `${page.url()}/edit`
        await page.goto(editUrl)
        // The control is a switch beside the label, not the label itself.
        await page.getByRole('switch').first().click()
        await page.getByRole('button', {name: /Speichern/}).last().click()

        await page.goto('/public/station/jugendfeuerwehr-musterstadt/blog')
        await expect(page.getByText(article).first()).toBeVisible()
    })

    /**
     * An article is rewritten more often than it is written. The story changes the title of one it
     * wrote and looks for the new title in the list, where everybody reads it.
     */
    test('an article is edited and shows its new title', async ({managerPage: page}) => {
        const article = unique('Neuigkeit')
        const renamed = unique('Umbenannt')

        await page.goto('/station/news')
        await page.getByRole('button', {name: 'Neuigkeit erstellen'}).click()
        await page.waitForURL(/\/station\/news\/create/)
        await page.getByPlaceholder('Titel der Neuigkeit').fill(article)
        const body = page.locator('[contenteditable="true"]').first()
        await body.click()
        await page.keyboard.type('Von der Story geschrieben.')
        await page.getByRole('button', {name: /Speichern|Veröffentlichen|Erstellen/}).last().click()

        await page.goto('/station/news')
        await page.getByText(article).first().click()
        await page.waitForURL(/\/station\/news\/\d+/)

        await page.goto(`${page.url()}/edit`)
        await page.getByPlaceholder('Titel der Neuigkeit').fill(renamed)
        await page.getByRole('button', {name: /Speichern/}).last().click()

        await page.goto('/station/news')
        await expect(page.getByText(renamed).first()).toBeVisible()
        await expect(page.getByText(article)).toHaveCount(0)
    })

    /**
     * An entry can hand a file over, which it could not before: authors used to paste a link to a
     * file living somewhere else. The story attaches one out of the station library and reads the
     * entry back as a member would, where the attachment is offered under the text rather than
     * buried inside it.
     */
    test('a file attached to an article is offered under it', async ({managerPage: page}) => {
        const article = unique('Protokoll')
        const detailUrl = await writeArticle(page, article, 'Das Protokoll hängt an.')

        await page.getByRole('button', {name: 'Datei anhängen'}).click()
        await page.getByTestId('media-file').filter({hasText: SEEDED_FILE}).first().click()

        await expect(page.getByText(SEEDED_FILE).first()).toBeVisible()
        await page.getByRole('button', {name: /Speichern/}).last().click()

        // The attachment is written after the entry itself, and the editor leaves for the list only
        // once both are through. Reading the entry before that races the attachment being stored.
        await page.waitForURL(/\/station\/news$/)

        await page.goto(detailUrl)
        const download = page.getByRole('link', {name: new RegExp(SEEDED_FILE)})
        await expect(download).toBeVisible()
        await expect(download).toHaveAttribute('href', /\/api\/v1\/public\/media\//)
    })

    /**
     * A longer entry can be built with the page editor instead of the single text field. The switch
     * carries the text already written into a block rather than parsing it, and it does not go
     * back, so the story asserts both: the text survives, and the offer to switch is gone.
     */
    test('an article switched to the page editor keeps its text', async ({managerPage: page}) => {
        const article = unique('Bericht')
        const written = 'Vor dem Umschalten geschrieben.'
        await writeArticle(page, article, written)

        await page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'}).click()

        await expect(page.getByText(written).first()).toBeVisible()
        await expect(page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'})).toHaveCount(0)

        await page.reload()
        await expect(page.getByText(written).first()).toBeVisible()
        await expect(page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'})).toHaveCount(0)
    })

    test('a member reads the news of their station', async ({memberPage: page}) => {
        await page.goto('/station/news')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
