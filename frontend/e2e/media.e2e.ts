/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * A file the demo seeder puts in the station library. The stories pick from the library rather than
 * uploading into it, because the stack they run against is a public demo and a public demo does not
 * accept uploads from whoever walks in. Picking is also the act worth covering: uploading is one way
 * a file arrives, using it everywhere is what moving the library out from under the pages was for.
 */
const SEEDED_FILE = 'fahrzeug.png'

test.describe('Media library', () => {
    /**
     * The library belongs to the station now, at the station's own address, rather than sitting
     * under the page editor where only a page author could reach it. The story goes straight there
     * and looks for the station's files.
     */
    test('the station library holds the station files', async ({managerPage: page}) => {
        await page.goto('/station/media')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(page.getByTestId('media-file').filter({hasText: SEEDED_FILE})).toBeVisible()
    })

    /**
     * The library opens from inside a text editor, which is the whole point of moving it out from
     * under the pages: what used to be reachable only from the page editor is now reachable from
     * every body a member can write. The story picks a file out of it and finds the picture in the
     * text, addressed by the hash of its bytes.
     */
    test('a picture is inserted into a text body from the library', async ({managerPage: page}) => {
        await page.goto('/station/news')
        await page.getByRole('button', {name: 'Neuigkeit erstellen'}).click()
        await page.waitForURL(/\/station\/news\/create/)

        const body = page.locator('[contenteditable="true"]').first()
        await body.click()
        await page.getByRole('button', {name: 'Image'}).click()
        await page.getByRole('button', {name: 'Medien'}).click()

        await page.getByTestId('media-file').filter({hasText: SEEDED_FILE}).first().click()

        await expect(body.locator('img[src*="/api/v1/public/media/"]')).toBeVisible()
    })

    /**
     * Inserting a picture is only half of it: the body is stored as text and rendered to HTML
     * before anybody reads it, and that render drops image sources it does not recognise. The story
     * reads a saved article back the way a member does, so a picture that is silently thrown away
     * on the way out fails here rather than in front of the author.
     */
    test('a picture inserted into an article survives being rendered', async ({managerPage: page}) => {
        await page.goto('/station/knowledge')
        await page.getByRole('button', {name: 'Neu'}).click()
        await page.getByText('Markdown-Datei').last().click()
        await page.getByPlaceholder('Dateiname').fill(unique('Bildartikel'))
        await page.getByRole('button', {name: 'Neue Datei'}).click()
        await page.waitForURL(/\/station\/knowledge\/file\/\d+/)
        const fileUrl = page.url()

        await page.getByRole('button', {name: 'Bearbeiten', exact: true}).first().click()
        await page.locator('.markdown-editor-content').click()
        await page.getByRole('button', {name: 'Image'}).click()
        await page.getByRole('button', {name: 'Medien'}).click()
        await page.getByTestId('media-file').filter({hasText: SEEDED_FILE}).first().click()
        await page.getByRole('button', {name: 'Speichern'}).last().click()
        await expect(page.getByText('Ungespeicherte Änderungen')).toHaveCount(0)

        await page.goto(fileUrl)
        await expect(page.locator('img[src*="/api/v1/public/media/"]')).toBeVisible()
    })

    /**
     * A picture placed on a page is read by people who never log in, and the public page is served
     * by a different route than the station's own view of it. The story reads the seeded showcase
     * page as a stranger, where a library file that only resolves for a member would show nothing.
     */
    test('a picture on a public page is served to a stranger', async ({page}) => {
        await page.goto('/public/station/jugendfeuerwehr-musterstadt/page/komponenten-schaukasten')

        // The page asks the server what the station is, and under a full suite that call sometimes
        // comes back short; it says so and offers another go, which is what a reader would press.
        const retry = page.getByRole('button', {name: 'Erneut versuchen'})
        if (await retry.count() > 0) await retry.click()

        await expect(page.locator('img[src*="/api/v1/public/media/"]').first()).toBeVisible()
    })
})
