/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Page} from '@playwright/test'
import {test, expect, apiHeaders, demoAccounts, pageAsThrowaway, type DemoAccount} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * Somebody of the station's team who runs none of its knowledge base.
 *
 * A grant naming the team has to change what such a reader sees, and only such a reader shows that:
 * anybody who manages the knowledge base reads past every grant by design, so a story driven by a
 * manager would prove nothing about the grant at all.
 */
async function teamReaderAt(request: APIRequestContext, stationId: string | undefined): Promise<DemoAccount> {
    const accounts = await demoAccounts(request)
    const reader = accounts.find(account => account.stationId === stationId
        && account.userType === 'TEAM'
        && !!account.email
        && !account.permissions.includes('STATION_ADMINISTRATOR')
        && !account.permissions.includes('STATION_MANAGER')
        && !account.permissions.includes('KNOWLEDGE_MANAGER')
        && !account.permissions.includes('KNOWLEDGE_EDIT'))
    if (!reader) throw new Error('The seeded station has no team member outside its knowledge base')
    return reader
}

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
    await createFolder(page, folder)
    await openFolder(page, folder)
    await createMarkdownFile(page, file)

    return {folder, file}
}

/**
 * Opens a folder of the listing and answers with its address.
 *
 * <p>It waits for the trail to name the folder rather than for the click to return: the address is
 * rewritten a moment after the click, so reading it straight away answers with the folder the
 * reader just left, and everything a story does afterwards happens in the wrong place.
 *
 * <p>The entry is reached through the listing rather than by its text alone, because a folder
 * picker that has been open names the same folders and would make the name answer twice.
 */
async function openFolder(page: Page, name: string): Promise<string> {
    await page.getByTestId('kb-item').filter({hasText: name}).click()
    await expect(page.getByTestId('kb-breadcrumb')).toContainText(name)
    return page.url()
}

/**
 * Opens the create menu of the listing currently open.
 *
 * <p>The button is named exactly: "Neuer Ordner" carries "Neu" inside it, so a dialog still on
 * screen from the entry before would otherwise make two buttons answer to the same name.
 */
async function openCreateMenu(page: Page) {
    const create = page.getByRole('button', {name: 'Neu', exact: true})
    await expect(create).toBeVisible()
    await create.click()
}

/** Creates a folder in the listing currently open, and waits for the dialog to be gone again. */
async function createFolder(page: Page, name: string) {
    await openCreateMenu(page)
    await page.getByText('Neuer Ordner').last().click()
    await page.getByPlaceholder('Ordnername').fill(name)
    await page.getByRole('button', {name: 'Neuer Ordner'}).last().click()
    await expect(page.getByPlaceholder('Ordnername')).toHaveCount(0)
    await expect(page.getByText(name).first()).toBeVisible()
}

/** Creates a Markdown article in the listing currently open, which opens it. */
async function createMarkdownFile(page: Page, name: string) {
    await openCreateMenu(page)
    await page.getByText('Markdown-Datei').last().click()
    await page.getByPlaceholder('Dateiname').fill(name)
    await page.getByRole('button', {name: 'Neue Datei'}).last().click()
    await page.waitForURL(/\/station\/knowledge\/file\/\d+/)
}

/**
 * Moves one entry of the listing currently open into a folder, or to the top level when no folder
 * is named. The entry offers several actions, so they sit behind a menu of their own per row.
 */
async function moveEntry(page: Page, entry: string, target: string | null) {
    await page.getByTestId('kb-item').filter({hasText: entry})
        .getByTestId('actions-menu-trigger').click()
    await page.getByRole('button', {name: 'Verschieben', exact: true}).click()
    const picker = page.getByTestId('kb-folder-picker')
    if (target === null) await page.getByTestId('kb-folder-picker-root').click()
    else await picker.getByRole('button', {name: target}).click()
    await page.getByTestId('kb-move-confirm').click()
    await expect(page.getByTestId('kb-move-confirm')).toHaveCount(0)
}

/** Ticks the box of one entry while the listing is in its marking mode. */
async function markEntry(page: Page, entry: string) {
    await page.getByTestId('kb-item').filter({hasText: entry}).getByTestId('kb-item-select').click()
}

/**
 * Opens the actions menu of the file header.
 *
 * <p>A file offers one action as a button of its own and keeps the rest behind that menu, so a
 * story that wants any of the rest has to open it first. The panel is rendered at the end of the
 * body rather than beside its button, which is also why nothing here reaches for a button by where
 * it stands on the page.
 */
async function openFileActions(page: Page) {
    await page.getByTestId('kb-file-actions-trigger').click()
    await page.getByTestId('kb-file-actions').waitFor()
}

/** The one action the file header shows as a button, which for a Markdown file is the editor. */
function headerButton(page: Page, name: string) {
    return page.getByTestId('kb-file-header').getByRole('button', {name, exact: true})
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
        await openFileActions(page)
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
            // The pen on the description line carries the same name, so the header is named rather
            // than counted on to come first.
            await headerButton(page, 'Bearbeiten').click()
            const body = page.locator('.markdown-editor-content')
            await body.click()
            await page.keyboard.press('ControlOrMeta+a')
            await page.keyboard.type(text)
            await page.getByRole('button', {name: 'Speichern'}).last().click()
            await expect(page.getByText('Ungespeicherte Änderungen')).toHaveCount(0)
        }

        await page.reload()
        await expect(page.getByText(second).first()).toBeVisible()

        await openFileActions(page)
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
     *
     * <p>It is marked from the article's own visibility dialog, not from its properties. Who may see
     * a thing is not a property of the thing the way its name is, and it has a place of its own.
     */
    test('a file marked public is readable on the public wiki', async ({managerPage: page, browser}) => {
        const {folder, file} = await createFileInFolder(page)

        await openFileActions(page)
        await page.getByRole('button', {name: 'Sichtbarkeit'}).click()
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

            await headerButton(page, 'Bearbeiten').click()
            const body = page.locator('.markdown-editor-content')
            await body.click()
            await page.keyboard.press('ControlOrMeta+a')
            await page.keyboard.type(written)
            await page.getByRole('button', {name: 'Speichern'}).last().click()
            await expect(page.getByText('Ungespeicherte Änderungen')).toHaveCount(0)

            await headerButton(page, 'Bearbeiten').click()
            await page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'}).click()

            await expect(page.getByText(written).first()).toBeVisible()
            await expect(page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'})).toHaveCount(0)

            await page.goto(fileUrl)
            await expect(page.getByText(written).first()).toBeVisible()

            await openFileActions(page)
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

            await headerButton(page, 'Bearbeiten').click()
            await page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'}).click()

            await page.goto(fileUrl)
            await expect(page.getByRole('button', {name: 'Mit dem Seiten-Editor schreiben'})).toHaveCount(0)
            await headerButton(page, 'Bearbeiten').click()
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

    /**
     * A branch put in the wrong place is the reason moving exists, so the story builds one three
     * levels deep and lifts the middle of it out. What matters afterwards is that the trail above
     * the folder reads differently while the address of the article inside it does not: a bookmark
     * on a page has to survive somebody tidying up around it.
     */
    test('a folder moves two levels up and the article inside it keeps its address',
        async ({managerPage: page}) => {
            const top = unique('Oben')
            const middle = unique('Mitte')
            const moved = unique('Unten')

            await page.goto('/station/knowledge')
            await createFolder(page, top)
            await openFolder(page, top)
            await createFolder(page, middle)
            const middleUrl = await openFolder(page, middle)
            await createFolder(page, moved)
            await openFolder(page, moved)
            const article = unique('Artikel')
            await createMarkdownFile(page, article)
            const articleUrl = page.url()

            // One level up, so the folder to move is an entry of the listing rather than the
            // listing itself.
            await page.goto(middleUrl)
            await moveEntry(page, moved, null)

            await page.goto('/station/knowledge')
            await expect(page.getByText(moved)).toBeVisible()
            await openFolder(page, moved)
            const trail = page.getByTestId('kb-breadcrumb')
            await expect(trail).not.toContainText(middle)
            await expect(page.getByText(article).first()).toBeVisible()

            await page.goto(articleUrl)
            await expect(page.getByTestId('kb-file-header')).toContainText(article)
        })

    /**
     * Marking several entries is worth having only if a selection that cannot go through as a whole
     * still does what it can. The story marks two entries where one of them collides with a name
     * already in the target, and reads the message: the article arrives, and the folder that stayed
     * behind is named rather than counted.
     */
    test('a marked selection moves what it can and names what it could not',
        async ({managerPage: page}) => {
            const parent = unique('Sammel')
            const target = unique('Ziel')
            const clashing = unique('Doppelt')

            await page.goto('/station/knowledge')
            await createFolder(page, parent)
            const parentUrl = await openFolder(page, parent)
            await createFolder(page, target)
            await createFolder(page, clashing)

            // The same name a second time, inside the target, which is what the move runs into.
            await openFolder(page, target)
            await createFolder(page, clashing)
            await page.goto(parentUrl)

            const article = unique('Mitgenommen')
            await createMarkdownFile(page, article)
            await page.goto(parentUrl)

            await page.getByTestId('kb-toggle-selecting').click()
            await markEntry(page, clashing)
            await markEntry(page, article)
            await expect(page.getByTestId('kb-selection-bar')).toContainText('2 ausgewählt')

            await page.getByTestId('kb-selection-move').click()
            await page.getByTestId('kb-folder-picker').getByRole('button', {name: target}).click()
            await page.getByTestId('kb-bulk-move-confirm').click()

            const notice = page.getByTestId('kb-bulk-notice')
            await expect(notice).toContainText('1 verschoben.')
            await expect(notice).toContainText(clashing)

            await openFolder(page, target)
            await expect(page.getByText(article).first()).toBeVisible()
        })

    /**
     * A reference written on one article has to be readable from the other end, or half of what a
     * wiki is for is missing. Nothing is written on the second article: the list it shows is the
     * same row read the other way round, which is also why it offers no way to remove it.
     */
    test('an article shows what points at it', async ({managerPage: page}) => {
        const folder = unique('Verweise')
        const source = unique('Quelle')
        const target = unique('Verweisziel')

        await page.goto('/station/knowledge')
        await createFolder(page, folder)
        const folderUrl = await openFolder(page, folder)
        await createMarkdownFile(page, target)
        const targetUrl = page.url()
        await page.goto(folderUrl)
        await createMarkdownFile(page, source)

        // The picker reads the wiki search, which indexes words rather than the whole name, so the
        // term is the readable part of it and the exact article is picked out of what comes back.
        await page.getByTestId('kb-add-related').click()
        await page.getByPlaceholder('Datei suchen...').fill('Verweisziel')
        await page.getByRole('button', {name: target}).click()
        await expect(page.getByRole('link', {name: target})).toBeVisible()

        await page.goto(targetUrl)
        const backlinks = page.getByTestId('kb-backlinks')
        await expect(backlinks).toContainText(source)
        await expect(backlinks.getByRole('button', {name: 'Entfernen'})).toHaveCount(0)
    })

    /**
     * A search reaches an article without walking the folders above it, so it is the one place a
     * reader can meet an entry that browsing would never have shown them. Two articles share a word
     * that appears nowhere else, one of them behind a folder only the team may open: the same
     * search has to find one and say nothing at all about the other, neither its name nor the
     * excerpt around the word.
     *
     * The searched word carries no punctuation, because the search splits on it: a name built the
     * way the other stories build theirs would be taken apart into terms that match nothing.
     */
    test('a search does not name an article the reader may not open',
        async ({managerPage, memberPage, browser, request}) => {
            const word = `Zauberwort${test.info().workerIndex}${Date.now().toString(36)}`
            const headers = await apiHeaders(managerPage)
            const openName = unique('Offener Artikel')
            const closedName = unique('Verschlossener Artikel')

            const closedFolder = await managerPage.request.post('/api/v1/kb/folders', {
                headers,
                data: {parentId: null, name: unique('Nur Team'), description: ''},
            })
            expect(closedFolder.ok(), `the folder was created (${await closedFolder.text()})`).toBeTruthy()
            const closedFolderId = (await closedFolder.json()).id

            for (const [folderId, name] of [[null, openName], [closedFolderId, closedName]] as const) {
                const written = await managerPage.request.post('/api/v1/kb/files/markdown', {
                    headers,
                    data: {folderId, name, description: '', content: `Das ${word} steht hier.`},
                })
                expect(written.ok(), `the article was written (${await written.text()})`).toBeTruthy()
            }

            const restricted = await managerPage.request.put(
                `/api/v1/kb/folders/${closedFolderId}/restrictions`,
                {
                    headers,
                    data: {
                        userTypes: ['TEAM'],
                        groupIds: [],
                        tagIds: [],
                        memberIds: [],
                        grants: [{userType: 'TEAM', level: null}],
                    },
                },
            )
            expect(restricted.ok(), `the folder was restricted (${await restricted.text()})`).toBeTruthy()

            await memberPage.goto('/station/knowledge')
            await memberPage.getByPlaceholder('Suchen...').fill(word)
            await expect(memberPage.getByText(openName)).toBeVisible()
            await expect(memberPage.getByText(closedName)).toHaveCount(0)

            const reader = await teamReaderAt(request, headers['X-Station-Id'])
            const readerPage = await pageAsThrowaway(browser, request, [], reader)
            await readerPage.goto('/station/knowledge')
            await readerPage.getByPlaceholder('Suchen...').fill(word)
            await expect(readerPage.getByText(openName)).toBeVisible()
            await expect(readerPage.getByText(closedName)).toBeVisible()

            await readerPage.context().close()
        })
})
