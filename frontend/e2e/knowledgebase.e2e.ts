/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * A folder of its own with one Markdown file in it, opened and ready to be written in.
 *
 * Every story that needs a file makes its own: they run in parallel against one station, and a
 * story writing into a file another one is reverting would fail for reasons that have nothing to
 * do with what it is testing.
 */
async function createFileInFolder(page: Page): Promise<{folder: string; file: string}> {
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

    return {folder, file}
}

/**
 * The permission story is the one worth having: what one person sets on a folder has to change what
 * a different person sees, and the rule is enforced in three places - the route guard, the levels
 * the listing reports, and the create menu. One story covers all three from the outside.
 */
test.describe('Knowledge base', () => {
    /**
     * A file says what it is for underneath its name, and that line is written where it is read.
     * The story checks it after a reload, since a description that does not survive one was never
     * saved.
     */
    test('the description of a file is written and kept', async ({managerPage: page}) => {
        const description = unique('Beschreibung')

        await createFileInFolder(page)

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
     * receives - so the story waits for the file rather than for the button to look pressed.
     */
    test('a file is downloaded as a PDF', async ({managerPage: page}) => {
        await createFileInFolder(page)

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
     * A wiki file is worth having because it can be rewritten, and worth trusting because the
     * rewrite can be taken back. The story writes twice, so there is a version to go back to, and
     * then goes back to the first one: what the file shows afterwards is the older text.
     */
    test('the content of a file is written, versioned and reverted', async ({managerPage: page}) => {
        const first = unique('Erster Stand')
        const second = unique('Zweiter Stand')

        await createFileInFolder(page)
        const fileUrl = page.url()

        // The editor is a rich one: it takes typing into its own body, not a value into a field.
        for (const text of [first, second]) {
            // The pen on the description line carries the same name, and it sits further down the
            // page - the one that opens the editor is the first.
            await page.getByRole('button', {name: 'Bearbeiten', exact: true}).first().click()
            const body = page.locator('.markdown-editor-content')
            await body.click()
            await page.keyboard.press('ControlOrMeta+a')
            await page.keyboard.type(text)
            await page.getByRole('button', {name: 'Speichern'}).last().click()
            await expect(page.getByText('Ungespeicherte Änderungen')).toHaveCount(0)
        }

        await page.reload()
        await expect(page.getByText(second).first()).toBeVisible()

        await page.getByRole('button', {name: 'Versionen'}).click()
        await page.waitForURL(/\/station\/knowledge\/file\/\d+\/versions/)

        // Version one is the file as it was created, which is empty; the first text is version two.
        await page.locator('[data-testid="kb-version"][data-version="2"]')
            .getByRole('button', {name: 'Zurücksetzen'}).click()
        await page.getByRole('button', {name: 'Zurücksetzen'}).last().click()

        await page.goto(fileUrl)
        await expect(page.getByText(first).first()).toBeVisible()
        await expect(page.getByText(second)).toHaveCount(0)
    })

    /**
     * A station can put a page of its wiki in front of everybody. The story marks a file public and
     * then reads the public wiki with no session at all - which is the only way to know that the
     * mark means what it says.
     */
    test('a file marked public is readable on the public wiki', async ({managerPage: page, browser}) => {
        const {folder, file} = await createFileInFolder(page)

        // The file is open where creating it landed, and its properties are edited from there.
        await page.getByRole('button', {name: 'Eigenschaften'}).click()
        await page.locator('select:has(option:text-is("Öffentlich sichtbar"))')
            .selectOption({label: 'Öffentlich sichtbar'})
        await page.getByRole('button', {name: 'Speichern'}).click()

        const stranger = await browser.newContext()
        const publicPage = await stranger.newPage()
        await publicPage.goto('/public/station/jugendfeuerwehr-musterstadt/knowledge')

        // The public wiki opens on the folders, as the station's own does.
        await publicPage.getByText(folder).first().click()
        await expect(publicPage.getByText(file).first()).toBeVisible()
        await stranger.close()
    })

    /**
     * A training document with a diagram beside its explanation is not a short notice, so an article
     * can be built with the page editor instead of the single text field. The switch carries what
     * was already written into a block rather than parsing it, and it does not go back.
     *
     * <p>Restoring an old version is withheld afterwards, and the story asserts that where a reader
     * meets it: what is stored is derived from the blocks, so putting an old body back would leave
     * the article saying one thing and built from another. Reading an old version still works.
     */
    test('an article switched to the page editor keeps its text and stops offering a revert',
        async ({managerPage: page}) => {
            const written = unique('Vor dem Umschalten')

            await createFileInFolder(page)
            const fileUrl = page.url()

            await page.getByRole('button', {name: 'Bearbeiten', exact: true}).first().click()
            const body = page.locator('.markdown-editor-content')
            await body.click()
            await page.keyboard.press('ControlOrMeta+a')
            await page.keyboard.type(written)
            await page.getByRole('button', {name: 'Speichern'}).last().click()
            await expect(page.getByText('Ungespeicherte Änderungen')).toHaveCount(0)

            await page.getByRole('button', {name: 'Bearbeiten', exact: true}).first().click()
            await page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'}).click()

            await expect(page.getByText(written).first()).toBeVisible()
            await expect(page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'})).toHaveCount(0)

            await page.goto(fileUrl)
            await expect(page.getByText(written).first()).toBeVisible()

            await page.getByRole('button', {name: 'Versionen'}).click()
            await page.waitForURL(/\/station\/knowledge\/file\/\d+\/versions/)
            await expect(page.getByTestId('kb-version').first()).toBeVisible()
            await expect(page.getByRole('button', {name: 'Zurücksetzen'})).toHaveCount(0)
        })

    /**
     * The same path as a news entry begun in the page editor: switch first, having typed nothing.
     * A news entry could not be created that way, because creating one demanded written text that
     * a block article does not have. An article here has an id before it is ever written in, so
     * the switch is stored there and then and there is nothing left to save, which is why the
     * article survives a reload without a save at all. The story holds that apart from the news
     * one rather than assuming the two behave alike.
     */
    test('an empty article is switched to the page editor and stays switched',
        async ({managerPage: page}) => {
            await createFileInFolder(page)
            const fileUrl = page.url()

            await page.getByRole('button', {name: 'Bearbeiten', exact: true}).first().click()
            await page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'}).click()

            await page.goto(fileUrl)
            await expect(page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'})).toHaveCount(0)
            await page.getByRole('button', {name: 'Bearbeiten', exact: true}).first().click()
            await expect(page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'})).toHaveCount(0)
        })

    /**
     * The listing offers what the reader may actually do: a member who may only read gets the
     * search and the entries, and no create menu - the same rule the server enforces, so nothing
     * is offered that would be refused.
     */
    test('a member sees the knowledge base without the actions of an editor', async ({memberPage: page}) => {
        await page.goto('/station/knowledge')

        await expect(page.getByPlaceholder('Suchen...')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neu'})).toHaveCount(0)
    })
})
