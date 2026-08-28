/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, stationPeers} from './fixtures/auth'

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
     * request reaches anybody - a request nobody can act on is a request that failed quietly.
     */
    test('equipment is asked for from a partner station', async ({managerPage: page}) => {
        await page.goto('/station/inventory/lending')

        // The offers and the requests each have a tab of their own, and the tab for requests carries
        // the same word as the button that sends one - so the button is the later of the two.
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

        // Each slot answered once, by index. The button is a toggle that keeps its wording either way,
        // so pressing "the first one" as many times as there are slots turns one of them on and off
        // again and leaves the rest unanswered.
        const missing = page.getByRole('button', {name: 'Nicht im Besitz'})
        const slots = await missing.count()
        for (let index = 0; index < slots; index += 1) {
            await missing.nth(index).click()
        }

        const finish = page.getByRole('button', {name: 'Prüfung abschließen'})
        await expect(finish).toBeEnabled()
        await finish.click()

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

    /**
     * A check that finds something raises the exchange there and then.
     *
     * Whoever walks a check has the member in front of them and the piece in their hands. Sending them
     * to another screen to type in what they are looking at is how a finding ends up recorded nowhere.
     * The two offered are the two that come up: it does not fit, and it is damaged.
     *
     * The size exchange is offered only where the piece has a size, so the walk looks for one that has
     * rather than assuming the first piece does.
     */
    test('a check raises an exchange for a piece that does not fit', async ({managerPage: page}) => {
        await page.goto('/station/inventory/checks/member')
        await page.getByRole('button', {name: 'Prüfung starten'}).first().click()
        await page.waitForURL(/\/station\/inventory\/checks\/(\d+)/)

        await page.getByRole('button', {name: 'Schnellprüfung'}).click()

        const sizeExchange = page.getByTestId('rapid-exchange-size').first()
        const damaged = page.getByTestId('rapid-exchange-damaged').first()
        await expect(damaged).toBeVisible({timeout: 15000})

        const offered = await sizeExchange.count()
        await (offered > 0 ? sizeExchange : damaged).click()

        await expect(page.getByTestId('rapid-exchange-reason')).toBeVisible()
        await expect(page.getByTestId('rapid-exchange-reason'), 'the reason is filled in already')
            .not.toHaveValue('')
        await page.getByTestId('rapid-exchange-confirm').click()

        await expect(page.getByTestId('rapid-exchange-confirm')).toBeHidden({timeout: 15000})

        const raised = await page.request.get('/api/v1/exchanges', {headers: await apiHeaders(page)})
            .then(r => r.json())
        expect(raised.length, 'the exchange is on the station\'s list').toBeGreaterThan(0)
    })

    /**
     * A correction is not a movement. The member is already holding the piece named in the window,
     * and pressing save only makes the record say so.
     */
    test('a check corrects which piece a member is holding', async ({managerPage: page}) => {
        await page.goto('/station/inventory/checks/member')
        await page.getByRole('button', {name: 'Prüfung starten'}).first().click()
        await page.waitForURL(/\/station\/inventory\/checks\/(\d+)/)

        const correct = page.locator('[data-testid^="correct-item-"]').first()
        await expect(correct).toBeVisible({timeout: 15000})
        const replaced = (await correct.getAttribute('data-testid')) ?? ''
        await correct.click()

        await expect(page.getByTestId('correct-old-piece'), 'the window says where the old piece goes')
            .not.toBeEmpty()

        const source = page.getByTestId('correct-source')
        if (await source.count() > 0) await source.selectOption('NEW')
        const size = page.getByTestId('correct-size')
        if (await size.count() > 0) await size.selectOption({index: 1})

        const stamp = Date.now()
        await page.getByTestId('correct-number').fill(`K-${stamp}`)
        await page.getByTestId('correct-confirm').click()

        await expect(page.getByTestId('correct-confirm')).toBeHidden({timeout: 15000})
        await expect(page.getByText(`K-${stamp}`), 'the member now holds what they really have').toBeVisible()
        await expect(page.getByTestId(replaced), 'and no longer what they never had').toBeHidden()
    })

    /**
     * An inventory the station already owns is written down from the member list rather than one
     * window at a time: a row per member, a size for all of them, one save.
     */
    test('a whole inventory is written down and handed out in one pass', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const stamp = Date.now()
        const made = await page.request.post('/api/v1/inventories',
            {headers, data: {name: `Aufnahme ${stamp}`, inventoryType: 'INTERNAL', hasSizes: true}})
        const inventoryId = (await made.json()).id
        await page.request.post(`/api/v1/inventories/${inventoryId}/sizes`, {headers, data: {label: 'M', position: 0}})

        await page.goto(`/station/inventory/intake/${inventoryId}`)
        await page.getByTestId('intake-load').click()

        const rows = page.getByTestId('intake-row')
        await expect(rows.first()).toBeVisible()

        await page.getByTestId('intake-bulk-size').selectOption({index: 1})
        await page.getByTestId('intake-apply-size').click()
        await page.getByTestId('intake-number-0').fill(`A-${stamp}`)
        await page.getByTestId('intake-save').click()

        await page.waitForURL(new RegExp(`/station/inventory/detail/${inventoryId}$`))

        const written = await page.request.get(`/api/v1/inventories/${inventoryId}/items`, {headers})
            .then(r => r.json())
        expect(written.length, 'every line of the table became a piece').toBeGreaterThan(1)
        expect(written.filter((item: {assignedTo?: number}) => item.assignedTo).length,
            'and each piece is in the hands of the member on its line').toBe(written.length)
        expect(written.some((item: {internalId?: string}) => item.internalId === `A-${stamp}`)).toBeTruthy()
    })

    /**
     * An inventory that keeps no sizes and no fields of its own, holding gear nobody ever wrote a
     * number on. There is nothing to fill in, so the line has to be asked for outright.
     */
    test('a piece with nothing to write down is taken into stock all the same', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const stamp = Date.now()
        const made = await page.request.post('/api/v1/inventories',
            {headers, data: {name: `Ohne Angaben ${stamp}`, inventoryType: 'INTERNAL', hasSizes: false}})
        const inventoryId = (await made.json()).id

        await page.goto(`/station/inventory/intake/${inventoryId}`)
        await page.getByTestId('intake-load').click()
        await expect(page.getByTestId('intake-row').first()).toBeVisible()

        await expect(page.getByTestId('intake-save'), 'an untouched table writes nothing').toBeDisabled()
        await page.getByTestId('intake-asked-0').check()
        await page.getByTestId('intake-save').click()

        await page.waitForURL(new RegExp(`/station/inventory/detail/${inventoryId}$`))

        const written = await page.request.get(`/api/v1/inventories/${inventoryId}/items`, {headers})
            .then(r => r.json())
        expect(written.length, 'the ticked line became a piece').toBe(1)
        expect(written[0].internalId ?? null, 'with no number on it').toBeNull()
        expect(written[0].assignedTo, 'in the hands of the member on its line').toBeTruthy()
    })
})
