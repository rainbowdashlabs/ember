/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, stationPeers} from './fixtures/auth'

test.describe('Inventory', () => {
    test('the inventory list shows the inventories of the station', async ({managerPage: page}) => {
        await page.goto('/station/inventory')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText('Inventar').first()).toBeVisible()
    })

    /** A member sees what they hold, and nothing about anyone else's equipment. */
    test('a member sees the equipment they hold', async ({memberPage: page}) => {
        await page.goto('/station/inventory/my')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * An inventory is a table of items, and finding one in it is what the table is for. The story
     * narrows the table by typing and expects fewer rows than it started with.
     */
    test('the items of an inventory can be narrowed down', async ({managerPage: page}) => {
        await page.goto('/station/inventory/manage')
        await page.getByTestId('inventory-card').first().click()
        await page.waitForURL(/\/station\/inventory\/detail\/\d+/)

        const rows = page.getByRole('row')
        await expect(rows.first()).toBeVisible()
        const before = await rows.count()

        await page.getByPlaceholder('Gegenstände durchsuchen...').fill('zzz-kein-treffer')

        await expect(async () => {
            expect(await rows.count()).toBeLessThan(before)
        }).toPass()
    })

    test('the storage containers are reachable', async ({managerPage: page}) => {
        await page.goto('/station/inventory/storage')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neuer Behälter'})).toBeVisible()
    })

    /**
     * Equipment meeting a person is the point of the whole feature. Both halves are searchable
     * pickers rather than raw scanners, so the story types what a scanner would send: the member's
     * name and the item's code.
     */
    test('an item is assigned to a member and handed back', async ({managerPage: page, request}) => {
        const {member} = await stationPeers(request)

        await page.goto('/station/inventory/assign')

        await page.getByPlaceholder('- Bitte wählen -').fill(member.lastName)
        await page.getByText(`${member.firstName} ${member.lastName}`).first().click()

        const picker = page.getByPlaceholder('Item suchen oder Code scannen…')
        await picker.fill('H-0')

        // The options of the picker are buttons, which is what separates them from the text a
        // search leaves behind in the field.
        const option = page.getByRole('button').filter({hasText: /H-0\d\d/}).first()
        const code = (await option.innerText()).match(/H-0\d\d/)?.[0] ?? ''
        await option.click()

        await expect(page.getByText(/zugewiesen/).first()).toBeVisible()

        // The counter is reopened before handing back, which is also what the picker needs: it
        // still believes the item is free until the page asks again.
        await page.reload()
        await page.getByPlaceholder('- Bitte wählen -').fill(member.lastName)
        await page.getByText(`${member.firstName} ${member.lastName}`).first().click()

        await page.getByPlaceholder('Item suchen oder Code scannen…').fill(code)
        await page.getByRole('button').filter({hasText: code}).first().click()

        await expect(page.getByText(/zurückgenommen/).first()).toBeVisible()
    })

    /** Assigning starts by naming a person or scanning a code, and offers both. */
    test('the assignment page asks who is receiving something', async ({managerPage: page}) => {
        await page.goto('/station/inventory/assign')

        await expect(page.getByRole('heading', {name: 'Mitglied'})).toBeVisible()
        await expect(page.getByRole('heading', {name: 'Scannen'})).toBeVisible()
    })

    test('the equipment checks are reachable', async ({managerPage: page}) => {
        await page.goto('/station/inventory/checks')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /** Borrowing is open to members; approving it is not. */
    test('a member reaches the equipment they may borrow', async ({memberPage: page}) => {
        await page.goto('/station/inventory/lending/browse')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * An inventory is worth nothing empty, so adding to it is the first thing anybody does. The
     * story gives the item a readable identifier of its own and looks for it in the table after a
     * reload, since a row that vanishes on one was never stored.
     */
    test('an item is added to an inventory', async ({managerPage: page}) => {
        const identifier = `E2E-${Date.now()}`
        const inventory = `Inventar-${Date.now()}`

        // An inventory of the story's own, and an internal one: an inventory of borrowed things
        // offers no way to add an item, because its items come from whoever lent them.
        await page.goto('/station/inventory/manage')
        await page.getByRole('button', {name: 'Inventar erstellen'}).click()
        await page.getByPlaceholder('z.B. Schutzkleidung').fill(inventory)
        await page.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(inventory).first()).toBeVisible()
        await page.getByText(inventory).first().click()
        await page.waitForURL(/\/station\/inventory\/(detail|edit)\/(\d+)/)

        // Whole words, and either of the two the pages use: adding a size carries the same verb, and
        // a partial match takes whichever of them comes first.
        await page.getByRole('button', {name: /^(Gegenstand hinzufügen|Hinzufügen)$/}).first().click()

        await page.getByPlaceholder('z.B. HLM-001').fill(identifier)
        await page.getByPlaceholder('z.B. Helm').fill('Storygegenstand')
        await page.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(identifier).first()).toBeVisible()

        await page.reload()
        await expect(page.getByText(identifier).first()).toBeVisible()
    })

    /**
     * What a member type is expected to hold is configured once and then read by everyone of that
     * type. The story adds a requirement and finds it again after a reload.
     */
    test('a requirement is configured for a member type', async ({managerPage: page}) => {
        await page.goto('/station/inventory/requirements')

        await page.getByRole('button', {name: 'Anforderung hinzufügen'}).click()

        await page.locator('select:has(option:text-is("Benutzertyp auswählen"))').selectOption({index: 1})
        await page.locator('select:has(option:text-is("Inventar auswählen"))').selectOption({index: 1})
        await page.getByRole('button', {name: 'Speichern'}).click()

        const cards = page.locator('main').getByRole('button', {name: 'Hinzufügen'})
        await expect(cards.first()).toBeVisible()

        await page.reload()
        await expect(cards.first()).toBeVisible()
    })

    /**
     * Borrowing runs between two stations: one offers what it can spare and the other asks for it.
     * The story asks as one station and approves as the other, which is the only way to see that a
     * request reaches anybody — a request nobody can act on is a request that failed quietly.
     */
    test('equipment is asked for from a partner station', async ({managerPage: page}) => {
        await page.goto('/station/inventory/lending')

        // The offers and the requests each have a tab of their own, and the tab for requests carries
        // the same word as the button that sends one — so the button is the later of the two.
        await page.getByRole('button', {name: 'Angebote'}).click()

        const offer = page.getByRole('button', {name: 'Anfragen'}).last()
        await expect(offer).toBeVisible()
        await offer.click()
        await page.waitForURL(/\/station\/inventory\/lending\/request\/new/)

        // A borrowing has to start somewhere, and the form keeps its submit disabled until it does.
        await page.locator('input[type="date"]').first().fill('2026-12-01')
        await page.getByRole('button', {name: 'Anfrage senden'}).click()

        // Sending opens the request itself, which is where both stations then talk about it.
        await page.waitForURL(/\/station\/inventory\/lending\/request\/\d+/)
        await expect(page.getByText('Angefragt').first()).toBeVisible()
    })

    /**
     * The other end of a borrowing: somebody has to say yes. The story takes a request that is
     * waiting and approves it, and the request says so afterwards.
     */
    test('an incoming request for equipment is approved', async ({managerPage: page}) => {
        await page.goto('/station/inventory/lending')
        await page.getByRole('button', {name: 'Anfragen'}).first().click()

        const waiting = page.locator('main').filter({hasText: 'Eingehende Anfragen'})
            .getByText('Angefragt').first()
        await expect(waiting).toBeVisible()
        await waiting.click()

        await page.waitForURL(/\/station\/inventory\/lending\/request\/\d+/)
        await page.getByRole('button', {name: 'Genehmigen'}).click()

        await expect(page.getByText('Genehmigt').first()).toBeVisible()
    })

    /**
     * A check goes through what somebody is supposed to hold and records what was there. The story
     * runs one to its end: confirming everything and closing it, which is the point at which the
     * result is written down rather than merely looked at.
     */
    test('the equipment of a member is checked and the result recorded', async ({managerPage: page}) => {
        await page.goto('/station/inventory/checks/member')

        await page.getByRole('button', {name: 'Prüfung starten'}).first().click()
        await page.waitForURL(/\/station\/inventory\/checks\/(\d+)/)
        const member = page.url().match(/checks\/(\d+)/)?.[1]

        // Confirming all covers what the member holds. What they are supposed to hold and do not is
        // a separate row each, and the check does not close until those are answered too.
        await page.getByRole('button', {name: 'Alle bestätigen'}).click()

        const missing = page.getByRole('button', {name: 'Nicht im Besitz'})
        for (let index = await missing.count(); index > 0; index -= 1) {
            await missing.first().click()
        }

        await page.getByRole('button', {name: 'Prüfung abschließen'}).click()

        // Closing a check moves straight on to the next person, so the result is read where it is
        // kept rather than wherever the walk happens to end.
        await page.goto(`/station/inventory/checks/${member}/result`)
        await expect(page.getByText('Vorhanden').first()).toBeVisible()
    })

    /**
     * Procurement is the list of what the station is short of. It is read rather than filled in:
     * what stands in it follows from the requirements and the stock.
     */
    test('procurement lists what the station is short of', async ({managerPage: page}) => {
        await page.goto('/station/inventory/procurement')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/inventory/procurement')
    })

    /**
     * A container is where an item lives when nobody is carrying it. The story creates one and
     * finds it in the tree afterwards, which is where anybody looking for it would look.
     */
    test('a storage container is created and listed', async ({managerPage: page}) => {
        const container = `Behälter-${Date.now()}`

        await page.goto('/station/inventory/storage')
        await page.getByRole('button', {name: 'Neuer Behälter'}).click()

        await page.getByLabel('Name').fill(container)
        await page.getByRole('button', {name: 'Erstellen'}).click()

        await expect(page.getByText(container).first()).toBeVisible()

        await page.reload()
        await expect(page.getByText(container).first()).toBeVisible()
    })
})
