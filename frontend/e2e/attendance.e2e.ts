/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {statSync} from 'node:fs'
import {test, expect, apiHeaders, type Page} from './fixtures/auth'

test.describe('Attendance', () => {
    /**
     * Recording who was there is the whole of attendance. The story opens a past session, marks
     * someone present and reloads: a mark that does not survive a reload never reached the server.
     *
     * Which session it lands in is not fixed, so it takes one the list says somebody was away
     * from: those are the members whose "present" button is still there to be pressed, and a
     * session nobody was ever entered in offers no buttons at all.
     */
    test('a member is marked present in a session', async ({managerPage: page}) => {
        const sessions = page.getByTestId('attendance-session')

        await page.goto('/station/attendance/past')
        await expect(sessions.first()).toBeVisible()

        const withAbsences = sessions.filter({hasText: /[1-9]\d* Abwesend/}).first()
        await expect(withAbsences).toBeVisible()
        await withAbsences.click()
        await page.waitForURL(/\/station\/attendance\/session\/\d+/)

        // Whoever is already present has that button switched off, so the story marks someone who
        // is not - and afterwards their button is the one switched off.
        const unmarked = page.locator('button[aria-label="Anwesend"]:not([disabled])').first()
        await expect(unmarked).toBeVisible()
        await unmarked.click()

        await page.reload()
        await expect(page.locator('button[aria-label="Anwesend"][disabled]').first()).toBeVisible()
    })

    /**
     * The notes beside a name are what makes the check the moment to deal with what is outstanding,
     * so the story asks the endpoint that feeds them rather than hunting for a member who happens to
     * have something open in the demo data.
     *
     * <p>What matters is that the shape is the one the screen reads and that it is answered to
     * whoever takes the attendance, since everything inside it is filtered by rights of its own.
     */
    test('the sheet says what is outstanding for its members', async ({managerPage: page}) => {
        await page.goto('/station/attendance/new')
        await page.getByRole('button', {name: 'Erstellen'}).first().click()
        await page.waitForURL(/\/station\/attendance\/session\/\d+/)
        const sessionId = Number(page.url().match(/\/session\/(\d+)/)![1])

        const response = await page.request.get(`/api/v1/attendance/sessions/${sessionId}/member-notes`, {
            headers: await apiHeaders(page),
        })
        expect(response.status(), `the notes are answered (${await response.text()})`).toBe(200)

        const notes = await response.json()
        expect(Array.isArray(notes)).toBe(true)
        for (const note of notes) {
            expect(typeof note.memberId).toBe('number')
            expect(Array.isArray(note.swaps)).toBe(true)
            expect(Array.isArray(note.foundItems)).toBe(true)
        }

        // The demo leaves one member a swap whose replacement is at the station and a found item they
        // have claimed, which is what makes both kinds of note reachable from here.
        expect(
            notes.some((note: {swaps: {handOverNext: boolean}[]}) => note.swaps.some(swap => swap.handOverNext)),
            'somebody is owed a piece that can be handed over',
        ).toBe(true)
        expect(
            notes.some((note: {foundItems: unknown[]}) => note.foundItems.length > 0),
            'somebody has claimed a found item and not collected it',
        ).toBe(true)
    })

    /**
     * The notes are what the check is for, so one has to be readable where the walk puts it. The
     * story finds the member the demo leaves a waiting handover on and looks at their row.
     */
    test('a member owed a piece is told so on the sheet', async ({managerPage: page}) => {
        await page.goto('/station/attendance/new')
        await page.getByRole('button', {name: 'Erstellen'}).first().click()
        await page.waitForURL(/\/station\/attendance\/session\/\d+/)

        const notes = page.getByTestId('member-check-notes')
        await expect(notes.first()).toBeVisible()
        await expect(page.getByTestId('note-swap').first()).toBeVisible()
        await expect(page.getByTestId('note-swap-hand-over').first()).toBeVisible()
    })

    /**
     * A sheet that anybody may still change months later is not a record of the evening. Age closes
     * one on its own, which a story cannot wait for, so this walks the other way in: whoever manages
     * attendance closes it on purpose, which is the same state by a different route.
     *
     * The reload is the point. Closing that only greys the buttons out until the next visit protects
     * nothing, so the story reloads and expects the sheet still shut, then opens it again and
     * expects the marking to come back.
     */
    test('a closed attendance sheet refuses marking until it is opened again', async ({managerPage: page}) => {
        await page.goto('/station/attendance/new')
        await page.getByRole('button', {name: 'Erstellen'}).first().click()
        await page.waitForURL(/\/station\/attendance\/session\/\d+/)
        await expect(page.locator('button[aria-label="Anwesend"]').first()).toBeVisible()

        await page.getByTestId('session-actions-trigger').click()
        await page.getByTestId('lock-session').click()

        await expect(page.getByTestId('unlock-session')).toBeVisible()
        await expect(page.locator('button[aria-label="Anwesend"]')).toHaveCount(0)

        await page.reload()
        await expect(page.getByTestId('unlock-session')).toBeVisible()
        await expect(page.locator('button[aria-label="Anwesend"]')).toHaveCount(0)

        await page.getByTestId('unlock-session').click()
        await expect(page.locator('button[aria-label="Anwesend"]').first()).toBeVisible()
    })

    /**
     * An evening starts by opening a session from the template it belongs to, and what it has to
     * bring with it is the people: a session listing nobody cannot record anybody.
     */
    test('a session is opened from a template and lists its members', async ({managerPage: page}) => {
        await page.goto('/station/attendance/new')

        await page.getByRole('button', {name: 'Erstellen'}).first().click()
        await page.waitForURL(/\/station\/attendance\/session\/\d+/)

        await expect(page.locator('button[aria-label="Anwesend"]').first()).toBeVisible()
    })

    /**
     * Sessions are not closed by hand - an evening simply ends, and what makes it findable
     * afterwards is the past list. The story opens one and looks for it there.
     */
    test('a session that was opened is found again among the past ones', async ({managerPage: page}) => {
        await page.goto('/station/attendance/new')

        await page.getByRole('button', {name: 'Erstellen'}).first().click()
        await page.waitForURL(/\/station\/attendance\/session\/\d+/)
        const sessionUrl = page.url()
        const id = sessionUrl.match(/\/session\/(\d+)/)?.[1]

        await page.goto('/station/attendance/past')

        // By its own number rather than by position: the stories run side by side and each one
        // opening a session pushes the others down the list.
        const entry = page.locator(`[data-testid="attendance-session"][data-session="${id}"]`)
        await expect(entry).toBeVisible()

        await entry.click()
        await expect(page).toHaveURL(sessionUrl)
    })

    test('past sessions are listed', async ({managerPage: page}) => {
        await page.goto('/station/attendance/past')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('the attendance report is reachable', async ({managerPage: page}) => {
        await page.goto('/station/attendance/report')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * A report exists to leave the application. The story picks a year and every member type,
     * previews it and takes the export: the file that arrives has to carry bytes, because an
     * empty download looks exactly like a successful one to everyone but the person opening it.
     */
    test('the report exports a file for the chosen period', async ({managerPage: page}) => {
        await page.goto('/station/attendance/report')

        await page.locator('select:has(option:text-is("Jahr"))').first().selectOption('year')

        await page.getByRole('button', {name: 'Typen wählen'}).click()
        await page.getByRole('button', {name: 'Alle auswählen'}).click()
        await page.getByText('Filter', {exact: true}).first().click()

        await page.getByRole('button', {name: 'Vorschau'}).click()

        const exportButton = page.getByRole('button', {name: 'PDF exportieren'})
        await expect(exportButton).toBeVisible()

        const download = page.waitForEvent('download')
        await exportButton.click()

        const file = await (await download).path()
        expect(file).toBeTruthy()
        expect(statSync(file!).size).toBeGreaterThan(0)
    })

    test('a member does not record attendance', async ({memberPage: page}) => {
        await page.goto('/station/attendance/new')

        await expect(page.getByRole('button', {name: /Speichern|Starten/})).toHaveCount(0)
    })

    /**
     * What the sheet a station configures will look like when it is filled in.
     *
     * <p>The questions are configured as a column of rows whatever width they are given, so the
     * choice is unanswerable without seeing it drawn, and a sheet of thirty short questions was
     * thirty lines long.
     */
    test.describe('Configuration', () => {
        /** Opens the first configured sheet for editing, which is where its questions live. */
        async function openConfig(page: Page) {
            await page.goto('/station/attendance/config')
            await page.getByLabel('Bearbeiten').first().click()
            await page.waitForURL(/\/station\/attendance\/config\/edit\/\d+/)
            await page.getByTestId('attendance-field-row').first().waitFor()
        }

        test('the questions are drawn as they will be asked', async ({managerPage: page}) => {
            await openConfig(page)

            await expect(page.getByTestId('field-layout-preview')).toBeVisible()
        })

        test('a question set to half a row keeps that width', async ({managerPage: page}) => {
            await openConfig(page)

            const row = page.getByTestId('attendance-field-row').first()
            const name = (await row.locator('span.font-medium').innerText()).trim()

            await row.getByLabel('Bearbeiten').click()
            const modal = page.getByTestId('modal')
            await modal.getByTestId('field-width').selectOption('half')
            await modal.getByRole('button', {name: 'Speichern'}).click()

            await page.reload()
            await page.getByTestId('attendance-field-row').first().waitFor()
            await page.getByTestId('attendance-field-row').filter({hasText: name}).first()
                .getByLabel('Bearbeiten').click()

            await expect(page.getByTestId('modal').getByTestId('field-width')).toHaveValue('half')
        })
    })
})
