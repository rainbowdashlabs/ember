/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {statSync} from 'node:fs'
import type {Locator} from '@playwright/test'
import {test, expect, apiHeaders, type Page} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * Acknowledges step after step until the swap stands on the one that puts the piece into the
 * member's hands, naming the replacement at the step that asks for it.
 *
 * <p>How many steps that takes depends on the chain, and whose gear it is decides the chain, so the
 * story walks until it is there rather than counting.
 */
async function walkUntilTheHandOver(
    page: Page,
    headers: Record<string, string>,
    movementId: number,
    replacementId: number,
): Promise<boolean> {
    for (let walked = 0; walked < 6; walked++) {
        const detail = await page.request
            .get(`/api/v1/movements/${movementId}`, {headers})
            .then(r => r.json())
        const standing = detail.steps.find((step: {current: boolean}) => step.current)
        if (!standing) return false
        if (standing.subject === 'INCOMING' && standing.custodyAfter === 'WITH_MEMBER') return true

        const answered = await page.request.post(`/api/v1/movements/${movementId}/acknowledge`, {
            headers,
            data: {stepId: standing.id, note: '', pickedItemId: standing.picksItem ? replacementId : null},
        })
        if (!answered.ok()) return false
    }
    return false
}

/**
 * Opens what stands beside a member's name on the sheet, which a row keeps folded away.
 *
 * <p>A station of forty people with a swap each would otherwise turn the sheet into a page of
 * errands, so a row says only how many there are until it is asked.
 *
 * @param page   the sheet
 * @param rows   the rows to open, or every row that has anything when none is named
 */
async function openNotes(page: Page, rows?: Locator) {
    const toggles = (rows ?? page.locator('body')).getByTestId('member-notes-toggle')
    await expect(toggles.first(), 'a row says there is something to see').toBeVisible()
    for (let index = 0; index < (await toggles.count()); index++) {
        const toggle = toggles.nth(index)
        if ((await toggle.getAttribute('aria-expanded')) !== 'true') await toggle.click()
    }
}

/**
 * Raises a swap on a piece somebody holds and walks it to the point where the replacement is at the
 * station, which is the one state that means "hand this over now".
 *
 * <p>Built by the story rather than borrowed from the demo, because handing a swap over uses it up.
 */
async function raiseSwapAwaitingHandover(page: Page, headers: Record<string, string>, onTheSheet: number[]) {
    for (const memberId of onTheSheet) {
        const member = {id: memberId}
        const items = await page.request
            .get(`/api/v1/station-members/${member.id}/inventory-items`, {headers})
            .then(r => r.json())
        for (const item of Array.isArray(items) ? items : []) {
            // The sizes are what make a replacement be picked out. Without them the swap reaches the
            // point of being called arrived while nothing was ever set aside, and handing over then
            // fails because there is no piece to hand.
            if (!item.inventoryHomogeneous || item.sizeId == null) continue
            const created = await page.request.post('/api/v1/movements', {
                headers,
                data: {
                    purpose: 'EXCHANGE',
                    memberId: member.id,
                    outgoingItemId: item.id,
                    inventoryId: item.inventoryId,
                    oldSizeId: item.sizeId,
                    newSizeId: item.sizeId,
                    reason: 'Von der Story angelegt',
                },
            })
            if (!created.ok()) continue

            const swap = (await created.json()).movement

            // Which piece the member gets has to be named, and naming it is what makes the swap one
            // that can be handed over at all. A free piece of the same inventory is the replacement.
            const spare = await page.request
                .get(`/api/v1/inventories/${item.inventoryId}/items`, {headers})
                .then(r => r.json())
                .then((all: {id: number; memberId?: number | null}[]) =>
                    (Array.isArray(all) ? all : []).find(candidate =>
                        candidate.id !== item.id && !candidate.memberId))
            if (!spare) continue

            if (await walkUntilTheHandOver(page, headers, swap.id, spare.id)) {
                return {...swap, memberId, replacementItemId: spare.id}
            }
        }
    }
    throw new Error('no piece was free to raise a swap on')
}

/** A moment as a date and time field holds it, on the clock the browser is reading. */
function asLocalInput(moment: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0')
    return `${moment.getFullYear()}-${pad(moment.getMonth() + 1)}-${pad(moment.getDate())}`
        + `T${pad(moment.getHours())}:${pad(moment.getMinutes())}`
}

/**
 * Opens a sheet from the first template offered, answering the step that asks when it runs.
 *
 * <p>The step is prefilled with the current time and the length the template last ran for, so the
 * ordinary evening is one further click and nothing has to be typed here.
 */
/**
 * Presses the export, wherever the toolbar is keeping it.
 *
 * <p>A sheet with names still to check offers the check as its button and puts the export in the
 * menu beside it; a sheet with nothing left to check offers the export itself. A story that opened
 * a sheet a moment ago cannot know which of the two it is looking at.
 */
async function openExport(page: Page) {
    const button = page.getByRole('button', {name: 'PDF Export'})
    if (await button.count() > 0) {
        await button.first().click()
        return
    }
    await page.getByTestId('session-actions-trigger').click()
    await page.getByTestId('session-actions').getByText('PDF Export').click()
}

async function openSheetFromTemplate(page: Page) {
    await page.getByRole('button', {name: 'Erstellen'}).first().click()
    await page.getByTestId('new-session-create').click()
    await page.waitForURL(/\/station\/attendance\/session\/\d+/)
}

test.describe('Attendance', () => {
    /**
     * A sheet for people no template describes, which is what took the empty template away.
     *
     * <p>The story asks for one member type and nothing else, so what lands on the sheet can be
     * checked against what the station says its members are. A template still lends its fields, and
     * its own groups must not come with them.
     */
    test('a sheet started without a template enters the types and groups it was told', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)

        await page.goto('/station/attendance/new')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.getByTestId('attendance-start-empty').click()
        await expect(page.getByTestId('attendance-audience-step')).toBeVisible()

        await page.getByTestId('attendance-type-TEAM').setChecked(true)
        await page.getByTestId('attendance-audience-confirm').click()
        await page.getByTestId('new-session-create').click()
        await page.waitForURL(/\/station\/attendance\/session\/\d+/)

        const sessionId = Number(page.url().match(/\/session\/(\d+)/)![1])
        const detail = await page.request
            .get(`/api/v1/attendance/sessions/${sessionId}`, {headers})
            .then(response => response.json())
        const entered: number[] = (detail.entries ?? []).map((entry: {memberId: number}) => entry.memberId)
        expect(entered.length, 'the sheet is filled rather than empty').toBeGreaterThan(0)

        const members = await page.request
            .get('/api/v1/station-members/rich', {headers})
            .then(response => response.json())
        const team = new Set(members
            .filter((member: {userType: string; formerAt: string | null}) => member.userType === 'TEAM')
            .map((member: {id: number}) => member.id))
        expect(
            entered.every(id => team.has(id)),
            'only the type that was chosen reached the sheet',
        ).toBeTruthy()
    })


    /**
     * Recording who was there is the whole of attendance. The story opens a past session, marks
     * someone present and reloads: a mark that does not survive a reload never reached the server.
     *
     * Which session it lands in is not fixed, so it takes one the list says somebody was away
     * from: those are the members whose "present" button is still there to be pressed, and a
     * session nobody was ever entered in offers no buttons at all.
     *
     * A sheet closes on its own once it is older than the span the instance allows, and the seeded
     * evenings with absences sit at that edge: which side of it they are on depends on the hour the
     * suite happens to run. So the sheet is opened again where it has frozen, which is what a
     * manager correcting an old evening does anyway, rather than the story passing before dinner
     * and failing after it.
     */
    test('a member is marked present in a session', async ({managerPage: page}) => {
        const sessions = page.getByTestId('attendance-session')

        await page.goto('/station/attendance/past')
        await expect(sessions.first()).toBeVisible()

        const withAbsences = sessions.filter({hasText: /[1-9]\d* Abwesend/}).first()
        await expect(withAbsences).toBeVisible()
        await withAbsences.click()
        await page.waitForURL(/\/station\/attendance\/session\/\d+/)

        const reopen = page.getByTestId('unlock-session')
        const marks = page.locator('button[aria-label="Anwesend"]')
        await expect(reopen.or(marks.first()).first()).toBeVisible()
        if (await reopen.count() > 0) await reopen.click()

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
        await openSheetFromTemplate(page)
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

        // The demo leaves swaps running and a claimed find, which is what makes both kinds of note
        // reachable at all. Whether any one of them is still outstanding is not asserted here: the
        // stories beside this one hand pieces over and sign finds off, so a count would race them.
        expect(
            notes.some((note: {swaps: unknown[]}) => note.swaps.length > 0),
            'the demo leaves somebody with a swap running',
        ).toBe(true)
    })

    /**
     * A field that attends by itself names people who were there, and an appointment can answer it
     * before the evening starts. Opening the sheet has to put them on it as present: they stood in
     * the field with no row at all until somebody pressed the button that fills the sheet in from
     * its appointment, which is not something anybody does before taking an attendance.
     */
    test('the people an appointment names in a self-attending field open the sheet as present', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)

        const template = await page.request
            .post('/api/v1/attendance/templates', {headers, data: {name: unique('Storybogen')}})
            .then(response => response.json())
        // Adding a field answers with the sheet's fields as they now stand, not with the one added.
        const field = await page.request
            .post(`/api/v1/attendance/templates/${template.id}/fields`, {
                headers,
                data: {
                    name: 'Betreuung',
                    fieldType: 'MEMBER_LIST',
                    config: {required: false, autoAttend: true},
                    position: 0,
                },
            })
            .then(response => response.json())
            .then((fields: {id: number; name: string}[]) => fields.find(entry => entry.name === 'Betreuung')!)

        const start = new Date(Date.now() + 86400000)
        const event = await page.request
            .post('/api/v1/events', {
                headers,
                data: {
                    name: unique('Storyabend'),
                    description: 'Von der Story angelegt',
                    eventType: 'ONE_TIME',
                    startTime: start.toISOString(),
                    endTime: new Date(start.getTime() + 3600000).toISOString(),
                    templateId: template.id,
                },
            })
            .then(response => response.json())

        const own = await page.request
            .get('/api/v1/session', {headers})
            .then(response => response.json())
            .then(session => session?.member?.id)

        // The tie is what carries the answer onto the sheet, and it only survives on a question of
        // the appointment the sheet is taken on, which is why the appointment names the template.
        const tied = await page.request.put(`/api/v1/events/${event.id}/fields`, {
            headers,
            data: {
                fields: [{
                    name: 'Betreuung',
                    fieldType: 'MEMBER_LIST',
                    value: `[${own}]`,
                    attendanceFieldId: field.id,
                }],
            },
        })
        expect(tied.ok(), `the question is tied to the sheet field (${await tied.text()})`).toBeTruthy()
        const tiedFields = await tied.json()
        expect(
            tiedFields[0]?.attendanceFieldId,
            'the tie survives, which is what carries the answer onto the sheet',
        ).toBe(field.id)

        const created = await page.request.post(`/api/v1/attendance/templates/${template.id}/sessions`, {
            headers,
            data: {eventId: event.id},
        })
        expect(created.status(), `the sheet is opened from the appointment (${await created.text()})`).toBe(201)
        const sessionId = (await created.json()).id

        const sheet = await page.request
            .get(`/api/v1/attendance/sessions/${sessionId}`, {headers})
            .then(response => response.json())
        expect(
            (sheet.entries ?? []).find((entry: {memberId: number}) => entry.memberId === own),
            'the person the appointment named is on the sheet',
        ).toMatchObject({status: 'PRESENT'})

        await page.goto(`/station/attendance/session/${sessionId}`)
        const row = page.getByTestId(`member-row-${own}`)
        await expect(row, 'the person the appointment named stands on the sheet').toBeVisible()
        await expect(
            row.locator('button[aria-label="Anwesend"][disabled]'),
            'and stands there as present, without anybody marking them',
        ).toBeVisible()

        await page.request.delete(`/api/v1/events/${event.id}`, {headers})
        await page.request.delete(`/api/v1/attendance/templates/${template.id}`, {headers})
    })

    /**
     * A swap whose replacement is at the station is handed over from the sheet itself, which is the
     * point of being told about it there.
     *
     * <p>The story raises its own swap rather than spending the one the demo leaves: handing that one
     * over consumes it, and a story that eats its own fixture passes once and fails every time after.
     */
    test('a swap waiting to be handed over is handed over from the sheet', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)

        await page.goto('/station/attendance/new')
        await openSheetFromTemplate(page)
        const sessionId = Number(page.url().match(/\/session\/(\d+)/)![1])

        // Whose swap it is has to be somebody the sheet lists, or the note is perfectly correct and
        // nowhere to be seen. The sheet says who those are.
        const detail = await page.request
            .get(`/api/v1/attendance/sessions/${sessionId}`, {headers})
            .then(response => response.json())
        const onTheSheet = (detail.entries ?? []).map((entry: {memberId: number}) => entry.memberId)
        const waiting = await raiseSwapAwaitingHandover(page, headers, onTheSheet)
        await page.reload()

        // Scoped to the note of this very swap. The same member may be waiting on several, ours
        // among them, and reaching for the first handover button would just as happily finish
        // somebody else's, which is data another story is standing on.
        const row = page.getByTestId(`member-row-${waiting.memberId}`)
        await openNotes(page, row)
        const handOver = row
            .locator(`[data-testid="note-swap"][data-swap="${waiting.id}"]`)
            .getByTestId('note-swap-step')
        await expect(handOver, 'our swap is waiting to be handed over').toBeVisible()

        const handed = page.waitForResponse(
            response => response.request().method() === 'POST' && response.url().includes('/acknowledge'),
        )
        await handOver.click()
        expect((await handed).status()).toBe(200)

        await expect(handOver).toHaveCount(0)

        const replacement = await page.request
            .get(`/api/v1/inventory-items/${waiting.replacementItemId}`, {headers})
            .then(response => response.json())
        expect(replacement.custody, 'the piece is in the member\'s hands, not merely hidden')
            .toBe('WITH_MEMBER')
    })

    /**
     * A swap nobody wants any more is called off from the sheet, with the member standing there to
     * say so. Nothing has changed hands while it waits there, so there is nothing to put back.
     */
    test('a swap that is no longer wanted is called off from the sheet', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)

        await page.goto('/station/attendance/new')
        await openSheetFromTemplate(page)
        const sessionId = Number(page.url().match(/\/session\/(\d+)/)![1])

        const detail = await page.request
            .get(`/api/v1/attendance/sessions/${sessionId}`, {headers})
            .then(response => response.json())
        const waiting = await raiseSwapAwaitingHandover(
            page,
            headers,
            (detail.entries ?? []).map((entry: {memberId: number}) => entry.memberId),
        )
        await page.reload()

        const row = page.getByTestId(`member-row-${waiting.memberId}`)
        await openNotes(page, row)
        const drop = row
            .locator(`[data-testid="note-swap"][data-swap="${waiting.id}"]`)
            .getByTestId('note-swap-drop')
        await expect(drop, 'our swap can be called off').toBeVisible()

        const dropped = page.waitForResponse(
            response => response.request().method() === 'DELETE' && response.url().includes('/movements/'),
        )
        await drop.click()
        await page.locator('[data-confirm]').click()
        expect((await dropped).status()).toBe(204)

        const left = await page.request
            .get('/api/v1/movements', {headers})
            .then(response => response.json())
        expect(
            left.some((entry: {id: number}) => entry.id === waiting.id),
            'the swap is gone rather than merely hidden',
        ).toBe(false)
    })

    /**
     * A found item is signed over from the sheet and stops being outstanding. The story claims one
     * for the manager themselves, which is who a claim may be made for without being their guardian.
     */
    test('a claimed find is signed over from the sheet', async ({managerPage: page, memberPage}) => {
        const headers = await apiHeaders(page)
        const memberHeaders = await apiHeaders(memberPage)

        // A claim may only be made for oneself or somebody in one's care, so the member claims it.
        // That also puts the note on a row the sheet actually lists, which the manager's own would
        // not be: the templates cover the two groups of members and not the team.
        const description = unique('Fundstueck')
        const found = await page.request
            .post('/api/v1/lost-and-found', {
                headers,
                data: {description, foundAt: new Date().toISOString().slice(0, 10)},
            })
            .then(response => response.json())
        const claimed = await memberPage.request.post(`/api/v1/lost-and-found/${found.id}/claim`, {
            headers: memberHeaders,
            data: {},
        })
        expect(claimed.ok(), `the member claims it for themselves (${await claimed.text()})`).toBeTruthy()

        await page.goto('/station/attendance/new')
        await openSheetFromTemplate(page)

        // Scoped to the note naming the find this story reported. The demo leaves a claimed find of
        // its own that another spec is standing on, and signing that one over would take it away.
        await openNotes(page)
        const signOff = page
            .getByTestId('note-found')
            .filter({hasText: description})
            .getByTestId('note-found-sign-off')
        await expect(signOff.first(), 'a find of ours is waiting to be collected').toBeVisible()
        const before = await signOff.count()

        const handed = page.waitForResponse(
            response => response.request().method() === 'POST' && response.url().includes('/provided'),
        )
        await signOff.first().click()
        await handed

        await expect(signOff).toHaveCount(before - 1)
    })

    /**
     * The notes are what the check is for, so one has to be readable where the walk puts it. The
     * story finds the member the demo leaves a waiting handover on and looks at their row.
     */
    test('a member owed a piece is told so on the sheet', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)

        await page.goto('/station/attendance/new')
        await openSheetFromTemplate(page)
        const sessionId = Number(page.url().match(/\/session\/(\d+)/)![1])

        // Its own swap rather than the one the demo leaves: the story beside this one hands a swap
        // over, and two stories reaching for the same one is a race whichever way it goes.
        const detail = await page.request
            .get(`/api/v1/attendance/sessions/${sessionId}`, {headers})
            .then(response => response.json())
        await raiseSwapAwaitingHandover(
            page,
            headers,
            (detail.entries ?? []).map((entry: {memberId: number}) => entry.memberId),
        )
        await page.reload()

        await openNotes(page)
        await expect(page.getByTestId('member-check-notes').first()).toBeVisible()
        await expect(page.getByTestId('note-swap').first()).toBeVisible()

        // The button carries the name of the step it walks rather than a word of its own, so what it
        // says is whatever the chain calls that step.
        const step = page.getByTestId('note-swap-step').first()
        await expect(step).toBeVisible()
        expect((await step.innerText()).trim(), 'the step is named on the button').not.toBe('')
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
        await openSheetFromTemplate(page)
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

        await openSheetFromTemplate(page)

        await expect(page.locator('button[aria-label="Anwesend"]').first()).toBeVisible()
    })

    /**
     * Sessions are not closed by hand - an evening simply ends, and what makes it findable
     * afterwards is the past list. The story opens one and looks for it there.
     */
    test('a session that was opened is found again among the past ones', async ({managerPage: page}) => {
        await page.goto('/station/attendance/new')

        await openSheetFromTemplate(page)
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

    /**
     * A camp runs from a Friday evening to a Sunday afternoon and has no appointment behind it, and
     * until the sheet was asked when it runs there was no way to write one down at all: a sheet made
     * from a template began and ended at the moment it was made.
     */
    test('a sheet is opened over several days without an appointment', async ({managerPage: page}) => {
        // Tomorrow rather than a fixed date: a sheet closes itself a week after its end, and a
        // closed one shows no times to write.
        const start = new Date(Date.now() + 86400000)
        start.setHours(18, 0, 0, 0)
        const end = new Date(start.getTime() + 44 * 3600000)

        await page.goto('/station/attendance/new')
        await page.getByRole('button', {name: 'Erstellen'}).first().click()

        await page.getByTestId('new-session-title').fill(unique('Zeltlager'))
        await page.getByTestId('new-session-start').fill(asLocalInput(start))
        await page.getByTestId('new-session-end').fill(asLocalInput(end))
        await page.getByTestId('new-session-create').click()
        await page.waitForURL(/\/station\/attendance\/session\/\d+/)

        const sessionId = Number(page.url().match(/\/session\/(\d+)/)![1])
        const sheet = await page.request
            .get(`/api/v1/attendance/sessions/${sessionId}`, {headers: await apiHeaders(page)})
            .then(r => r.json())

        const spanHours =
            (new Date(sheet.session.endTime).getTime() - new Date(sheet.session.startTime).getTime()) / 3_600_000
        expect(spanHours).toBe(44)

        // A sheet over several days writes a member's times with their day, since a time alone
        // would name two moments and the one that was picked decides the hours.
        await page.locator('button[aria-label="Anwesend"]:not([disabled])').first().click()
        const memberMoments = page.locator('[data-testid^="member-row-"] input[type="datetime-local"]')
        await expect(memberMoments.first()).toBeVisible()
        expect(await memberMoments.first().inputValue()).toBe(asLocalInput(start))
    })

    /**
     * The hours the report adds up are what a station pays against, and the clock is not always the
     * number that is owed. The sheet keeps its times and says what a presence at it is worth.
     */
    test('what a sheet counts as is kept and reaches the report', async ({managerPage: page}) => {
        await page.goto('/station/attendance/new')
        await openSheetFromTemplate(page)
        const sessionId = Number(page.url().match(/\/session\/(\d+)/)![1])

        const counted = page.getByTestId('session-counted-hours')
        await counted.fill('3')
        await counted.blur()

        await expect.poll(async () => {
            const sheet = await page.request
                .get(`/api/v1/attendance/sessions/${sessionId}`, {headers: await apiHeaders(page)})
                .then(r => r.json())
            return sheet.session.countedMinutes
        }).toBe(180)

        await page.reload()
        await expect(page.getByTestId('session-counted-hours')).toHaveValue('3')
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

    /**
     * A sheet handed out to be signed. The story opens one of its own, asks for the signature column
     * and room for people nobody expected, and takes the file: an export that renders nothing is a
     * download of no bytes, which is what the size says.
     */
    test('a sheet is printed to be signed by hand', async ({managerPage: page}) => {
        await page.goto('/station/attendance/new')
        await openSheetFromTemplate(page)

        // The dialog that opened the sheet fades out over the page, and its backdrop swallows the
        // press underneath it while it does.
        await expect(page.getByTestId('modal')).toHaveCount(0)
        await openExport(page)

        const dialog = page.getByTestId('export-sheet-modal')
        await expect(dialog).toBeVisible()
        await dialog.getByTestId('export-signature-toggle').getByRole('switch').click()
        await dialog.getByRole('spinbutton').fill('4')

        const download = page.waitForEvent('download')
        await dialog.getByTestId('export-submit').click()

        const file = await (await download).path()
        expect(file).toBeTruthy()
        expect(statSync(file!).size, 'the sheet carries bytes').toBeGreaterThan(0)
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
