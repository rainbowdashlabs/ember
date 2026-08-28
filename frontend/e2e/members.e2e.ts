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
     * An address is corrected precisely when it is wrong, so waiting for the wrong address to confirm
     * the change would mean it never happens. Whoever may edit members writes it, and it stands.
     */
    test('a manager puts a wrong address right', async ({managerPage: page}) => {
        const created = await createMember(page)
        const address = `${unique('neu').toLowerCase()}@test.example`

        await page.goto('/station/members/list')
        await page.getByPlaceholder(/Suche/).first().fill(created)
        await page.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
        await page.waitForURL(/\/station\/members\/detail\/(\d+)/)
        const id = page.url().match(/detail\/(\d+)/)?.[1]

        await page.goto(`/station/members/edit/${id}`)
        // First name, surname and address, in that order: the labels above them are text rather than
        // labels an input is tied to.
        await page.getByRole('textbox').nth(2).fill(address)
        await page.getByRole('button', {name: 'Speichern'}).first().click()

        await page.goto(`/station/members/edit/${id}`)
        await expect(page.getByRole('textbox').nth(2), 'the new address is the one on the account')
            .toHaveValue(address)
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
     * The profile as it is read, rather than as it is filled in.
     *
     * <p>Two things were only ever checked on the form that writes them. A heading was rendered on
     * the reading page as though it were a question with no answer, "Ausrüstung: -", which turns the
     * arrangement the station made into noise. And a date was shown exactly as it is stored, so a
     * birthday read 2019-11-03 instead of the 03.11.2019 it is.
     */
    test('the profile of a member reads with its headings and its dates', async ({managerPage: page}) => {
        const heading = unique('Abschnitt')
        const dateField = unique('Eintritt')
        const created = await createMember(page)

        await page.goto('/station/members/config')
        await page.getByRole('button', {name: 'Feld hinzufügen'}).first().click()
        const sectionDialog = page.getByRole('dialog')
        await sectionDialog.getByPlaceholder('Name des Feldes').fill(heading)
        await sectionDialog.getByRole('combobox').first().selectOption('SECTION')
        await sectionDialog.getByRole('button', {name: 'Speichern'}).click()

        await page.getByRole('button', {name: 'Feld hinzufügen'}).first().click()
        const dateDialog = page.getByRole('dialog')
        await dateDialog.getByPlaceholder('Name des Feldes').fill(dateField)
        await dateDialog.getByRole('combobox').first().selectOption('DATE')
        await dateDialog.getByRole('button', {name: 'Speichern'}).click()
        await expect(page.getByText(dateField).first()).toBeVisible()

        await page.goto('/station/members/list')
        await page.getByPlaceholder(/Suche/).first().fill(created)
        await page.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
        await page.waitForURL(/\/station\/members\/detail\/(\d+)/)
        const id = page.url().match(/detail\/(\d+)/)?.[1]

        await page.goto(`/station/members/edit/${id}`)
        await page.locator(`[data-field="${dateField}"] input`).fill('2019-11-03')
        const save = page.locator('.save-button').last()
        await save.click()
        await expect(save, 'the answer was kept before the page is left').toHaveClass(/bg-success/)

        await page.goto(`/station/members/detail/${id}`)
        await expect(page.getByTestId('field-section').filter({hasText: heading}),
            'the heading stands as one').toHaveCount(1)
        await expect(page.getByTestId('field-entry').filter({hasText: heading}),
            'and not as a question nobody answered').toHaveCount(0)
        await expect(page.locator(`[data-testid="field-entry"][data-field="${dateField}"]`),
            'a date reads the way a date is written here').toContainText('03.11.2019')
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

        // Semicolons, because that is the separator the member wizard starts with.
        await uploadCsv(page, `Vorname;Nachname\nTestperson;${surname}\n`)

        // Each column of the file is pointed at what it holds; the wizard refuses to go on until at
        // least the name is answered for.
        await mapColumn(page, 'Vorname', 'firstName')
        await mapColumn(page, 'Nachname', 'lastName')

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
     * A row struck out in the preview stays out, and reading the same list again adds nobody.
     *
     * <p>Both are what a station does with a list it exports afresh every year: one line belongs to
     * somebody who left, and everybody else is already here. Without either, the second reading left
     * the station with two of everybody.
     *
     * <p>The second walk takes the whole file, struck-out row included: the one already here is
     * passed over and the other joins, which is what the reading is for.
     */
    test('a struck out row is left behind and a second reading adds nobody', async ({managerPage: page}) => {
        const stays = unique('Bleibt')
        const struck = unique('Gestrichen')
        const csv = `Vorname;Nachname\nTestperson;${stays}\nTestperson;${struck}\n`

        async function walkTheWizard() {
            await uploadCsv(page, csv)
            await mapColumn(page, 'Vorname', 'firstName')
            await mapColumn(page, 'Nachname', 'lastName')
            await page.getByRole('button', {name: 'Vorschau'}).click()
            await expect(page.getByTestId('preview-row')).toHaveCount(2)
        }

        await walkTheWizard()
        await page.getByTestId('preview-row').filter({hasText: struck}).getByTestId('toggle-row').click()
        await expect(page.getByTestId('preview-row').filter({hasText: struck})).toHaveClass(/line-through/)

        await page.getByRole('button', {name: 'Importieren'}).click()
        await expect(page.getByText('Import abgeschlossen')).toBeVisible()

        await page.goto('/station/members/list')
        await page.getByPlaceholder(/Suche/).first().fill(stays)
        await expect(page.getByTestId('member-row')).toHaveCount(1)
        await page.getByPlaceholder(/Suche/).first().fill(struck)
        await expect(page.getByTestId('member-row'), 'the struck out row was never imported').toHaveCount(0)

        await walkTheWizard()
        await page.getByRole('button', {name: 'Importieren'}).click()
        await expect(page.getByText('Import abgeschlossen')).toBeVisible()

        await page.goto('/station/members/list')
        await page.getByPlaceholder(/Suche/).first().fill(stays)
        await expect(page.getByTestId('member-row'), 'nobody was doubled').toHaveCount(1)
        await page.getByPlaceholder(/Suche/).first().fill(struck)
        await expect(page.getByTestId('member-row')).toHaveCount(1)
    })

    /**
     * Points one column of the file at what it holds, found by the heading it carries.
     *
     * <p>By what the target is worth rather than by what it is called: every column offers "Vorname"
     * twice over, once for the member and once for each of their guardians, and a story picking by
     * name would take whichever came first.
     */
    async function mapColumn(page: Page, column: string, target: string | {label: string}) {
        await page.locator(`[data-testid="mapping-row"][data-column="${column}"]`).first()
            .locator('select').first()
            .selectOption(target)
    }

    /** Puts a file in front of the wizard and carries it to the mapping step. */
    async function uploadCsv(page: Page, csv: string) {
        await page.goto('/station/members/import')
        await page.setInputFiles('input[type="file"]',
            {name: 'mitglieder.csv', mimeType: 'text/csv', buffer: Buffer.from(csv, 'utf-8')})
        await page.getByRole('button', {name: 'Weiter'}).click()
        await expect(page.getByTestId('mapping-row').first()).toBeVisible()
    }

    /** Opens the imported person, found by the surname they were read in under. */
    async function openImported(page: Page, surname: string) {
        await page.goto('/station/members/list')
        await page.getByPlaceholder(/Suche/).first().fill(surname)
        await page.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
        await page.waitForURL(/\/station\/members\/detail\/\d+/)
    }

    /**
     * A youth list as a station actually keeps one: names, answers to the station's own questions,
     * a group, and a parent on every row. Everything the reading is for at once, because each part
     * of it broke on its own while the parts beside it went on working.
     *
     * <p>The answers are read back off the member rather than off the report the import writes: a
     * count of what was set says nothing about whether it reached the person it was set on, which is
     * exactly how a mapped question came to be dropped while the wizard said it had been kept.
     */
    test('a list with questions, a group and a parent is read in whole', async ({managerPage: page}) => {
        const surname = unique('Vollstaendig')
        const parent = unique('Elternteil')
        const allergy = unique('Heuschnupfen')

        await uploadCsv(page, 'Vorname;Nachname;Allergie;Geburtstag;Kontakt;Telefon;Kontakt Email\n'
            + `Testperson;${surname};${allergy};04.03.2011;Anja ${parent};01700000000;`
            + `${parent.toLowerCase()}@example.test\n`)

        await mapColumn(page, 'Vorname', 'firstName')
        await mapColumn(page, 'Nachname', 'lastName')
        await mapColumn(page, 'Allergie', {label: 'Allergien (Text)'})
        await mapColumn(page, 'Geburtstag', {label: 'Geburtstag (Datum)'})
        await mapColumn(page, 'Kontakt', 'manager:1:firstName')
        await mapColumn(page, 'Telefon', 'manager:1:phone')
        await mapColumn(page, 'Kontakt Email', 'manager:1:email')

        await page.getByRole('button', {name: 'Vorschau'}).click()
        await expect(page.getByText(/1 Mitglieder erkannt/)).toBeVisible()
        await page.getByRole('button', {name: 'Importieren'}).click()
        await expect(page.getByText('Import abgeschlossen')).toBeVisible()

        await openImported(page, surname)
        await expect(page.getByText(allergy).first(), 'the answer reached the member').toBeVisible()

        await page.getByRole('button', {name: 'Erziehungsberechtigte'}).click()
        await expect(page.getByTestId('guardian-row'), 'the parent was written down and linked').toHaveCount(1)
        await expect(page.getByTestId('guardian-row').first()).toContainText(parent)
    })

    /**
     * A parent the list gives no address for is still a parent.
     *
     * <p>A youth list carries a telephone number far more often than an address, and a contact
     * without one used to be dropped where it stood: no guardian, no link, and nothing said. An
     * address is made up for them the same way it is for a member who arrives without one.
     */
    test('a parent without an address is written down all the same', async ({managerPage: page}) => {
        const surname = unique('Ohnemail')
        const parent = unique('Namenlos')

        await uploadCsv(page, `Vorname;Nachname;Kontakt;Telefon\nTestperson;${surname};Bea ${parent};01700000000\n`)

        await mapColumn(page, 'Vorname', 'firstName')
        await mapColumn(page, 'Nachname', 'lastName')
        await mapColumn(page, 'Kontakt', 'manager:1:firstName')
        await mapColumn(page, 'Telefon', 'manager:1:phone')

        await page.getByRole('button', {name: 'Vorschau'}).click()
        await page.getByRole('button', {name: 'Importieren'}).click()
        await expect(page.getByText('Import abgeschlossen')).toBeVisible()

        await openImported(page, surname)
        await page.getByRole('button', {name: 'Erziehungsberechtigte'}).click()
        await expect(page.getByTestId('guardian-row')).toHaveCount(1)
        await expect(page.getByTestId('guardian-row').first()).toContainText(parent)
    })

    /**
     * The editor that maps a column's answers onto the station's own offers each answer once.
     *
     * <p>A column of thirty rows answered in three ways is three things to map, not thirty. It used
     * to list the first few rows as they stood, so the same answer arrived several times over and
     * the ones further down the file never arrived at all.
     *
     * <p>The question has its own answers, so the target is picked from them rather than typed: one
     * spelled by hand matches nothing, and the value then arrives exactly as the file had it.
     */
    test('the value editor offers every answer of a column once', async ({managerPage: page}) => {
        await uploadCsv(page, 'Vorname;Nachname;Geschlecht\n'
            + 'Eine;Person;m\nZweite;Person;m\nDritte;Person;w\nVierte;Person;m\n')

        await mapColumn(page, 'Geschlecht', {label: 'Geschlecht (Auswahl)'})
        await page.locator('[data-testid="mapping-row"][data-column="Geschlecht"]')
            .getByRole('button', {name: 'Werte zuordnen'}).click()

        await expect(page.getByTestId('value-map-row'), 'm and w, each once').toHaveCount(2)

        const target = page.getByTestId('value-map-target').first()
        await target.selectOption({label: 'männlich'})
        await expect(target).toHaveValue('männlich')
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
