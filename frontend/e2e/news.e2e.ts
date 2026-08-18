/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

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

    test('a member reads the news of their station', async ({memberPage: page}) => {
        await page.goto('/station/news')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
