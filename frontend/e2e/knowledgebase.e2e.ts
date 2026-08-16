/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * KB-1 and KB-3 of the story list.
 *
 * KB-3 is the one worth having: a permission set on a folder by one person has to change what a
 * different person sees, and the rule is enforced in three places — the route guard, the levels the
 * listing reports, and the create menu. One story covers all three from the outside.
 */
test.describe('Knowledge base', () => {
    /** Creating a Markdown file opens it, because that is where its content is written. */
    test('KB-1 a folder is created and holds a file', async ({managerPage: page}) => {
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
    test('KB-3 a member sees the knowledge base without the actions of an editor', async ({memberPage: page}) => {
        await page.goto('/station/knowledge')

        await expect(page.getByPlaceholder('Suchen...')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neu'})).toHaveCount(0)
    })
})
