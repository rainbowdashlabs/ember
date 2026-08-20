/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {statSync} from 'node:fs'
import type {Page} from '@playwright/test'
import {test, expect, accountWithout, pageAsThrowaway} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * Somebody new, through the wizard a manager uses. It walks several steps before it writes
 * anything, and each one has to be carried past on its own, so every story that needs a member of
 * its own goes through here rather than borrowing a seeded one the others are also using.
 */
async function createMember(page: Page): Promise<string> {
    const surname = unique('Story')

    await page.goto('/station/members/create')
    await expect(page.getByTestId('app-shell')).toBeVisible()

    await page.getByRole('button', {name: 'Weiter'}).first().click()

    await page.getByPlaceholder('Vorname').fill('Testperson')
    await page.getByPlaceholder('Nachname').fill(surname)
    await page.getByPlaceholder('E-Mail-Adresse').fill(`${surname.toLowerCase()}@example.test`)
    await page.getByRole('button', {name: 'Weiter'}).first().click()

    for (let step = 0; step < 4; step += 1) {
        const next = page.getByRole('button', {name: /Weiter|Konto erstellen|Erstellen/}).first()
        if (!await next.isVisible().catch(() => false)) break
        await next.click()
    }

    return surname
}

test.describe('Members', () => {
    test('a member is created through the wizard', async ({managerPage: page}) => {
        const surname = await createMember(page)

        await page.goto('/station/members/list')
        await expect(page.getByText(surname).first()).toBeVisible()
    })

    /**
     * Somebody the station still has something with cannot simply be written off - equipment in
     * their hands, profiles in their care. The page refuses and says what stands in the way, and
     * the reason being given is the part worth holding.
     *
     * The story looks for such a person rather than naming one: who holds what changes as the rest
     * of the suite hands equipment out and takes it back, and a story that insists on one member
     * would be testing the seeder's mood.
     */
    test('a member with something outstanding cannot be marked former', async ({managerPage: page}) => {
        const warning = page.getByText('Dieses Mitglied kann derzeit nicht als ehemalig markiert werden:')

        const rows = page.getByTestId('member-row')

        await page.goto('/station/members/list')
        await expect(rows.first()).toBeVisible()

        for (let index = 0; index < Math.min(await rows.count(), 6); index += 1) {
            if (index > 0) {
                await page.goto('/station/members/list')
                await expect(rows.first()).toBeVisible()
            }

            await rows.nth(index).getByRole('button', {name: 'Details'}).click()
            await page.waitForURL(/\/station\/members\/detail\/\d+/)

            const mark = page.getByRole('button', {name: 'Als ehemalig markieren'})
            const offered = await mark.waitFor({state: 'visible', timeout: 5_000}).then(() => true, () => false)
            if (!offered) continue

            await mark.click()
            await expect(page.getByText('Mitglied als ehemalig markieren')).toBeVisible()

            const refused = await warning.waitFor({state: 'visible', timeout: 5_000}).then(() => true, () => false)
            if (!refused) continue

            // What stands in the way differs from person to person - equipment they hold, profiles
            // they look after - so the story holds the page to naming something rather than to one
            // reason.
            await expect(page.getByRole('listitem').first()).toBeVisible()
            return
        }

        throw new Error('No member of the station had anything outstanding to be held back by')
    })

    /**
     * Somebody who joined and left again without ever taking anything out has nothing standing in
     * the way, so the same button confirms rather than warns - and they leave the active list.
     */
    test('a member with nothing outstanding is marked former', async ({managerPage: page}) => {
        const surname = unique('Abschied')

        await page.goto('/station/members/create')
        await page.getByRole('button', {name: 'Weiter'}).first().click()
        await page.getByPlaceholder('Vorname').fill('Testperson')
        await page.getByPlaceholder('Nachname').fill(surname)
        await page.getByPlaceholder('E-Mail-Adresse').fill(`${surname.toLowerCase()}@example.test`)
        await page.getByRole('button', {name: 'Weiter'}).first().click()
        for (let step = 0; step < 4; step += 1) {
            const next = page.getByRole('button', {name: /Weiter|Konto erstellen|Erstellen/}).first()
            if (!await next.isVisible().catch(() => false)) break
            await next.click()
        }

        await page.goto('/station/members/list')
        await page.getByPlaceholder(/Suche/).first().fill(surname)
        await page.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
        await page.waitForURL(/\/station\/members\/detail\/\d+/)

        await page.getByRole('button', {name: 'Als ehemalig markieren'}).first().click()
        await expect(page.getByText('Mitglied als ehemalig markieren')).toBeVisible()
        await page.getByRole('button', {name: 'Als ehemalig markieren'}).nth(1).click()

        await page.goto('/station/members/former')
        await expect(page.getByText(surname).first()).toBeVisible()
    })

    test('the member list shows the station and filters by name', async ({managerPage: page}) => {
        await page.goto('/station/members/list')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        const rows = page.getByRole('row')
        await expect(rows.first()).toBeVisible()

        const before = await rows.count()
        await page.getByPlaceholder(/Suche/).first().fill('zzzz-kein-treffer')
        await expect(async () => {
            expect(await rows.count()).toBeLessThan(before)
        }).toPass()
    })

    /**
     * A member's own details are the point of the member list. The story creates somebody, changes
     * their name and looks at the change from the detail page - where whoever needs it reads it,
     * rather than in the form that wrote it.
     */
    test('the details of a member are edited', async ({managerPage: page}) => {
        const surname = unique('Umbenannt')
        const created = await createMember(page)

        await page.goto('/station/members/list')
        await page.getByPlaceholder(/Suche/).first().fill(created)
        await page.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
        await page.waitForURL(/\/station\/members\/detail\/(\d+)/)
        const id = page.url().match(/detail\/(\d+)/)?.[1]

        await page.goto(`/station/members/edit/${id}`)
        // First name, surname and address, in that order: the labels above them are text rather than
        // labels an input is tied to.
        await page.getByRole('textbox').nth(1).fill(surname)
        await page.getByRole('button', {name: 'Speichern'}).first().click()

        await page.goto(`/station/members/detail/${id}`)
        await expect(page.getByText(surname).first()).toBeVisible()
    })

    /**
     * A tag is how a station marks a handful of people as belonging together without giving them a
     * group. The story makes one and puts somebody in it.
     */
    test('a tag is created and a member carries it', async ({managerPage: page}) => {
        const tag = unique('Tag')

        await page.goto('/station/members/tags')
        await page.getByRole('button', {name: 'Tag erstellen'}).click()
        await page.getByPlaceholder('Tag-Name eingeben').fill(tag)
        await page.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(tag).first()).toBeVisible()
        await page.getByText(tag).first().click()

        const candidate = page.getByTestId('tag-candidate').first()
        await expect(candidate).toBeVisible()
        const name = (await candidate.innerText()).split('\n')[0]
        await candidate.click()

        await page.reload()
        await page.getByText(tag).first().click()
        await expect(page.getByText(name).first()).toBeVisible()
    })

    /**
     * A station asks its members for things no other station asks for, so it can add a field of its
     * own. The story adds one and then finds it where it has to appear: in the form that edits a
     * member.
     */
    test('a custom member field is configured and offered on a member', async ({managerPage: page}) => {
        const field = unique('Feld')
        const created = await createMember(page)

        await page.goto('/station/members/config')
        await page.getByRole('button', {name: 'Feld hinzufügen'}).first().click()
        await page.getByPlaceholder('Name des Feldes').fill(field)
        await page.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(field).first()).toBeVisible()

        await page.goto('/station/members/list')
        await page.getByPlaceholder(/Suche/).first().fill(created)
        await page.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
        await page.waitForURL(/\/station\/members\/detail\/(\d+)/)
        const id = page.url().match(/detail\/(\d+)/)?.[1]

        await page.goto(`/station/members/edit/${id}`)
        await expect(page.getByText(field).first()).toBeVisible()
    })

    /**
     * A heading is not a field: it is asked of nobody and holds no answer. It earns its place by
     * appearing among the fields where the station put it, which is what turns a long list into
     * something that reads.
     */
    test('a heading is placed among the fields and asks for nothing', async ({managerPage: page}) => {
        const heading = unique('Abschnitt')

        await page.goto('/station/members/config')
        await page.getByRole('button', {name: 'Feld hinzufügen'}).first().click()
        const dialog = page.getByRole('dialog')
        await dialog.getByPlaceholder('Name des Feldes').fill(heading)
        await dialog.getByRole('combobox').first().selectOption('SECTION')

        await expect(dialog.getByText('Pflichtfeld')).toHaveCount(0)

        await dialog.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(heading).first()).toBeVisible()
    })

    /**
     * A field of group scope is listed at its group and nowhere else, so the group it was made for
     * has to survive being saved. It travels as one opaque lump of configuration, which no type on
     * either side describes, so only walking both ends says whether it arrived.
     */
    test('a field made for a group is still at that group afterwards', async ({managerPage: page}) => {
        const field = unique('Gruppenfeld')

        await page.goto('/station/members/config')
        await page.getByRole('button', {name: 'Gruppenspezifisch'}).click()
        await page.getByRole('combobox').first().selectOption({index: 1})
        await page.getByRole('button', {name: 'Feld hinzufügen'}).first().click()
        await page.getByPlaceholder('Name des Feldes').fill(field)
        await page.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(field).first()).toBeVisible()

        await page.reload()
        await page.getByRole('button', {name: 'Gruppenspezifisch'}).click()
        await page.getByRole('combobox').first().selectOption({index: 1})
        await expect(page.getByText(field).first()).toBeVisible()
    })

    /**
     * A station arriving with its members in a spreadsheet imports them. The story walks the whole
     * wizard and then looks for the imported person in the member list, which is the only place
     * that says the import did anything.
     */
    test('members are imported from a file', async ({managerPage: page}) => {
        const surname = unique('Importiert')

        await page.goto('/station/members/import')

        await page.setInputFiles('input[type="file"]', {
            name: 'mitglieder.csv',
            mimeType: 'text/csv',
            // Semicolons, because that is the separator the member wizard starts with.
            buffer: Buffer.from(`Vorname;Nachname\nTestperson;${surname}\n`, 'utf-8'),
        })
        await page.getByRole('button', {name: 'Weiter'}).click()

        // Each column of the file is pointed at what it holds; the wizard refuses to go on until at
        // least the name is answered for.
        await page.locator('select:has(option:text-is("Vorname"))').first().selectOption({label: 'Vorname'})
        await page.locator('select:has(option:text-is("Nachname"))').last().selectOption({label: 'Nachname'})

        await page.getByRole('button', {name: 'Vorschau'}).click()
        await expect(page.getByText(/1 Mitglieder erkannt/)).toBeVisible()

        await page.getByRole('button', {name: 'Importieren'}).click()
        await expect(page.getByText('Import abgeschlossen')).toBeVisible()

        // Searched for rather than read off the row: the address the import derives from the name
        // is what makes the person findable, and one match is one imported member.
        await page.goto('/station/members/list')
        await page.getByPlaceholder(/Suche/).first().fill(surname)
        await expect(page.getByTestId('member-row')).toHaveCount(1)
    })

    /**
     * A station that has to hand a list of its members to somebody else exports one. The story
     * walks the whole picking - export mode, a member, the columns - and takes the file, because a
     * file with nothing in it looks like a success until it is opened.
     */
    test('the member list exports a file', async ({managerPage: page}) => {
        await page.goto('/station/members/list')

        await page.getByRole('button', {name: 'Exportieren'}).click()
        await page.getByRole('checkbox').nth(1).check()
        await page.getByRole('button', {name: 'Weiter'}).click()

        const download = page.waitForEvent('download')
        await page.getByRole('button', {name: 'Exportieren'}).last().click()

        const file = await (await download).path()
        expect(statSync(file!).size).toBeGreaterThan(0)
    })

    /**
     * What a member changes about themselves is not silently taken over: it waits for somebody to
     * look at it. The story reads that list, which is where a station notices a new address.
     */
    test('the changes members made are listed for the manager', async ({managerPage: page}) => {
        await page.goto('/station/members/changes')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Offene Änderungen'})).toBeVisible()
    })

    /**
     * What is written about a member is not for everyone who may look at them. The station keeps one
     * helper who may read the members but not their notes, which is the whole point of the story:
     * the manager writes a note and the helper, on the same member, is not even offered the tab.
     */
    test('a note is shown to whoever may read notes and hidden from the rest', async ({managerPage, browser, request}) => {
        const note = unique('Notiz')

        await managerPage.goto('/station/members/list')
        await managerPage.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
        await managerPage.waitForURL(/\/station\/members\/detail\/(\d+)/)
        const id = managerPage.url().match(/detail\/(\d+)/)?.[1]

        // The note is one field per member rather than a list of entries, so what says it was kept
        // is the field still holding it after a reload.
        await managerPage.getByRole('button', {name: 'Notizen'}).click()
        await managerPage.getByPlaceholder(/Notiz schreiben/).fill(note)
        await managerPage.getByRole('button', {name: 'Speichern'}).last().click()

        await managerPage.reload()
        await managerPage.getByRole('button', {name: 'Notizen'}).click()
        await expect(managerPage.getByPlaceholder(/Notiz schreiben/)).toHaveValue(note)

        const helper = await accountWithout(request, 'TEAM', 'MEMBER_NOTES')
        const helperPage = await pageAsThrowaway(browser, request, [], helper)

        await helperPage.goto(`/station/members/detail/${id}`)
        await expect(helperPage.getByTestId('app-shell')).toBeVisible()
        await expect(helperPage.getByRole('button', {name: 'Notizen'})).toHaveCount(0)
        await expect(helperPage.getByText(note)).toHaveCount(0)

        await helperPage.context().close()
    })

    /**
     * A permission is not a hidden button: the page has to be unreachable for someone without it,
     * which is what stops a guessed URL from working.
     */
    test('a member without the right cannot open the member list', async ({memberPage: page}) => {
        await page.goto('/station/members/list')

        await expect(page.getByRole('table')).toHaveCount(0)
    })
})
