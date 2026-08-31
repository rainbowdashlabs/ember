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
     * A note written while walking a check is kept with the piece it is about.
     *
     * <p>The walk shows one piece at a time and used to take a decision and nothing else, so whoever
     * had something to say about a piece had to remember it until the long list. The story writes the
     * note where it is now asked for and reads it back off the finished check, because a note that is
     * typed and not kept is worse than no field at all.
     */
    test('a note written during the quick check is kept', async ({managerPage: page}) => {
        const note = `Saum offen ${test.info().workerIndex}-${Date.now()}`

        await page.goto('/station/inventory/checks/member')

        // A member of this story's own where there is one: closing a check is the last thing it does,
        // and the story that closes the first member's would be closing the same one.
        const starts = page.getByRole('button', {name: 'Prüfung starten'})
        await expect(starts.first()).toBeVisible({timeout: 15000})
        await ((await starts.count()) > 1 ? starts.nth(1) : starts.first()).click()
        await page.waitForURL(/\/station\/inventory\/checks\/(\d+)/)
        const member = page.url().match(/checks\/(\d+)/)?.[1]

        await page.getByRole('button', {name: 'Schnellprüfung'}).click()

        const noteField = page.getByTestId('rapid-note')
        await expect(noteField).toBeVisible({timeout: 15000})
        await noteField.fill(note)

        // The walk is answered to its end: a piece in hand is confirmed, an empty place is one the
        // member never had. Only a check that is closed writes anything down.
        const present = page.getByRole('button', {name: 'Vorhanden'})
        const neverHeld = page.getByRole('button', {name: 'Nicht im Besitz'})
        const done = page.getByRole('button', {name: 'Zurück zur Übersicht'})
        for (let step = 0; step < 50; step += 1) {
            await expect(present.or(neverHeld).or(done).first()).toBeVisible({timeout: 15000})
            if (await done.isVisible()) break
            if (await present.isVisible()) await present.click()
            else await neverHeld.click()
        }
        await expect(done, 'the walk reaches its end').toBeVisible({timeout: 15000})

        const finish = page.getByRole('button', {name: 'Prüfung abschließen'})
        await expect(finish).toBeEnabled()
        await finish.click()

        await page.goto(`/station/inventory/checks/${member}/result`)
        await expect(page.getByText(note)).toBeVisible({timeout: 15000})
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
     *
     * One button opens it and the window asks why, so the reason a station gives is not limited to the
     * ones somebody thought of: the two common ones are offered, and anything else is written out. The
     * window opens on a reason already chosen, because a check that has to be filled in before it can
     * be saved is a check nobody raises.
     */
    test('a check raises an exchange for a piece that does not fit', async ({managerPage: page}) => {
        await page.goto('/station/inventory/checks/member')
        await page.getByRole('button', {name: 'Prüfung starten'}).first().click()
        await page.waitForURL(/\/station\/inventory\/checks\/(\d+)/)

        await page.getByRole('button', {name: 'Schnellprüfung'}).click()

        const exchange = page.getByTestId('rapid-exchange').first()
        await expect(exchange).toBeVisible({timeout: 15000})
        await exchange.click()

        const confirm = page.getByTestId('rapid-exchange-confirm')
        await expect(confirm, 'whether the piece was handed over is still unanswered').toBeDisabled()

        await page.getByTestId('rapid-exchange-reason-other').click()
        await page.getByTestId('rapid-exchange-reason').fill('Reißverschluss fehlt')
        await expect(confirm, 'a reason alone does not answer the question').toBeDisabled()

        await page.getByTestId('rapid-exchange-kept').click()
        await expect(confirm, 'reason and answer together stand').toBeEnabled()

        await page.getByTestId('rapid-exchange-reason-damaged').click()
        await confirm.click()

        await expect(confirm).toBeHidden({timeout: 15000})

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

    /**
     * An inventory says whether it holds one thing in many copies or a drawer of different things,
     * and the three features that only mean something for the first stop offering themselves for
     * the second. The story marks a drawer, then goes to the screen that writes requirements and
     * expects it not to be on offer there.
     */
    test('a drawer of different things is not offered where one thing is meant', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const stamp = Date.now()
        const drawer = `Gemeindekiste ${stamp}`
        await page.request.post('/api/v1/inventories', {
            headers,
            data: {name: drawer, inventoryType: 'INTERNAL', hasSizes: false, homogeneous: false},
        })

        await page.goto('/station/inventory/requirements')
        await page.getByTestId('requirement-add').click()

        const picker = page.getByTestId('requirement-inventory')
        await expect(picker).toBeVisible()
        // The picker has something in it, so an empty list is not what makes the next line pass
        await expect(picker.getByRole('option')).not.toHaveCount(1)
        await expect(picker.getByRole('option', {name: drawer})).toHaveCount(0)

        // and the inventory really does exist; it is this screen that does not offer it
        const listed = await page.request.get('/api/v1/inventories', {headers}).then(r => r.json())
        expect(listed.some((inv: {name?: string}) => inv.name === drawer)).toBeTruthy()
    })

    /**
     * Splitting an inventory is a move rather than a delete and a rewrite, which is what keeps the
     * pieces the pieces they were. The story moves one and expects it in the other inventory with
     * the identifier it started with.
     */
    test('a piece moves to another inventory and stays the piece it was', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const stamp = Date.now()
        const from = await page.request.post('/api/v1/inventories',
            {headers, data: {name: `Bundhose leicht ${stamp}`, inventoryType: 'INTERNAL', hasSizes: false}})
            .then(r => r.json())
        const to = await page.request.post('/api/v1/inventories',
            {headers, data: {name: `Bundhose schwer ${stamp}`, inventoryType: 'INTERNAL', hasSizes: false}})
            .then(r => r.json())
        const code = `BH-${stamp}`
        const made = await page.request.post(`/api/v1/inventories/${from.id}/items`, {
            headers,
            data: {internalId: code, name: 'Bundhose', sizeId: null, metadata: null,
                ownerKind: 'STATION', ownerClusterId: null},
        })
        expect(made.ok(), `a piece is recorded (${await made.text()})`).toBeTruthy()
        const item = await made.json()

        await page.goto(`/station/inventory/move/${from.id}`)
        await page.getByTestId('move-target').selectOption({label: `Bundhose schwer ${stamp}`})
        await page.getByTestId('move-select-all').check()
        await page.getByTestId('move-submit').click()

        await expect(page.getByTestId('move-done')).toBeVisible()

        const moved = await page.request.get(`/api/v1/inventories/${to.id}/items`, {headers})
            .then(r => r.json())
        expect(moved.length, 'the piece arrived').toBe(1)
        expect(moved[0].id, 'as the same row it always was').toBe(item.id)
        expect(moved[0].internalId, 'with the number it started with').toBe(code)
    })

    /**
     * Tidying a drawer up into kinds. The point of the story is the rename: setting a kind leaves
     * the name alone, and the name is what every list and both exports read, so a typo would go on
     * reading as a typo under a heading that says otherwise. Merging therefore rewrites it.
     */
    test('two spellings of one thing become one kind, and the typo is written out', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const stamp = Date.now()
        const drawer = await page.request.post('/api/v1/inventories', {
            headers,
            data: {name: `Funkgeräte ${stamp}`, inventoryType: 'INTERNAL', hasSizes: false, homogeneous: false},
        }).then(r => r.json())

        const right = 'Funkgerät orange'
        const typo = 'Funkgerät organge'
        for (const name of [right, right, typo]) {
            const made = await page.request.post(`/api/v1/inventories/${drawer.id}/items`, {
                headers,
                data: {internalId: null, name, sizeId: null, artId: null, metadata: null,
                    ownerKind: 'STATION', ownerClusterId: null},
            })
            expect(made.ok(), `a piece is recorded (${await made.text()})`).toBeTruthy()
        }

        await page.goto(`/station/inventory/tidy/${drawer.id}`)
        await expect(page.getByTestId('tidy-names')).toBeVisible()

        await page.getByTestId(`tidy-name-${right}`).check()
        await page.getByTestId(`tidy-name-${typo}`).check()
        await page.getByTestId('tidy-art-name').fill(right)
        await page.getByTestId('tidy-merge').click()

        await expect(page.getByTestId('tidy-done')).toBeVisible()

        const arts = await page.request.get(`/api/v1/inventories/${drawer.id}/arts`, {headers})
            .then(r => r.json())
        expect(arts.length, 'one kind was written down, and only one').toBe(1)
        expect(arts[0].name).toBe(right)

        const pieces = await page.request.get(`/api/v1/inventories/${drawer.id}/items`, {headers})
            .then(r => r.json())
        expect(pieces.every((p: {artId?: number}) => p.artId === arts[0].id),
            'every piece is of that kind').toBeTruthy()
        expect(pieces.some((p: {name?: string}) => p.name === typo),
            'and the misspelling is gone from the names as well').toBeFalsy()
    })

    /**
     * A word put on a piece finds that piece again, whatever inventory it is filed under and
     * however the word is typed the second time. The word is written into the picker rather than
     * created beforehand, because making one up on the spot is the ordinary way a word comes to
     * exist.
     */
    test('a word is put on a piece and finds it again', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const stamp = Date.now()
        const word = `Funk${stamp}`

        const drawer = await page.request.post('/api/v1/inventories', {
            headers,
            data: {name: `Funklager ${stamp}`, inventoryType: 'INTERNAL', hasSizes: false, homogeneous: true},
        }).then(r => r.json())
        const made = await page.request.post(`/api/v1/inventories/${drawer.id}/items`, {
            headers,
            data: {internalId: `FK-${stamp}`, name: 'Ladestation', sizeId: null, artId: null, metadata: null,
                ownerKind: 'STATION', ownerClusterId: null},
        })
        expect(made.ok(), `a piece is recorded (${await made.text()})`).toBeTruthy()
        const piece = await made.json()

        await page.goto(`/station/inventory/detail/${drawer.id}`)
        await page.getByTestId('actions-menu-trigger').first().click()
        await page.getByTestId('actions-menu').getByText('Bearbeiten').click()

        const picker = page.getByTestId('item-tags')
        await expect(picker).toBeVisible()
        await picker.getByTestId('label-select').click()
        await picker.getByPlaceholder('Suchen oder erstellen...').fill(word)
        await picker.getByTestId('label-select-create').click()
        await page.getByTestId('modal').getByRole('button', {name: 'Speichern'}).click()

        await expect(async () => {
            const worn = await page.request.get(`/api/v1/inventory-items/${piece.id}/tags`, {headers})
                .then(r => r.json())
            expect(worn.map((tag: {name: string}) => tag.name), 'the piece wears the word').toContain(word)
        }).toPass()

        const found = await page.request.get('/api/v1/inventory-tags/items', {
            headers,
            params: {tag: ` ${word.toLowerCase()} `},
        }).then(r => r.json())
        expect(found.map((item: {itemId: number}) => item.itemId),
            'and the word finds it again however it is typed').toContain(piece.id)
    })
})
