/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, demoAccounts, pageAsThrowaway, type Page} from './fixtures/auth'
import {unique} from './fixtures/unique'
import type {APIRequestContext, Browser} from '@playwright/test'
import {must} from './fixtures/must'

/**
 * A file the demo seeder puts in the main station's library. Picking one is what attaching an event
 * file is, so that is what the stories do wherever there is something to pick.
 */
const SEEDED_FILE = 'fahrzeug.png'

/**
 * Somebody who runs the named station, signed in.
 *
 * <p>Which station shares an event with which is the seeder's business and not a story's, so the
 * owning side is found from the answer rather than assumed: the reader names the station its files
 * come from, and this signs in as whoever runs it.
 */
async function managerOfStation(browser: Browser, request: APIRequestContext, stationUid: string): Promise<Page> {
    const runsIt = (await demoAccounts(request)).find(account => account.stationId === stationUid
        && !!account.email
        && (account.permissions.includes('STATION_ADMINISTRATOR')
            || account.permissions.includes('STATION_MANAGER')))
    expect(runsIt, `station ${stationUid} has a manager to act as`).toBeTruthy()
    return pageAsThrowaway(browser, request, [], runsIt)
}

/** The smallest thing a library takes: one transparent pixel, for a station the seeder left empty. */
const PIXEL = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
    'base64',
)

/** A file of the station's library: the seeded one where there is one, an uploaded one otherwise. */
async function libraryFileId(page: Page): Promise<number> {
    const headers = await apiHeaders(page)
    const response = await page.request.get('/api/v1/media/files', {headers})
    expect(response.ok(), 'the station library answers').toBe(true)
    const listing: {file: {id: number; fileName: string}}[] = await response.json()
    const held = listing.find(entry => entry.file.fileName === SEEDED_FILE) ?? listing[0]
    if (held) return held.file.id

    const uploaded = await page.request.post('/api/v1/media/files', {
        headers,
        multipart: {file: {name: 'laufzettel.png', mimeType: 'image/png', buffer: PIXEL}},
    })
    expect(uploaded.status(), await uploaded.text()).toBe(201)
    return (await uploaded.json()).id
}

/** An event of the station, taken from the planner the same way a reader reaches one. */
async function someEventId(page: Page): Promise<number> {
    await page.goto('/station/events')
    const entry = page.getByTestId('event-entry').first()
    await expect(entry).toBeVisible()
    await entry.click()
    await page.waitForURL(/\/station\/events\/\d+/)
    return Number(page.url().match(/\/events\/(\d+)/)![1])
}

async function attach(page: Page, eventId: number, fileId: number, label: string, internal: boolean) {
    const headers = await apiHeaders(page)
    const response = await page.request.post(`/api/v1/events/${eventId}/attachments`, {
        headers,
        data: {fileId, label, internal},
    })
    expect(response.status(), await response.text()).toBe(201)
    return (await response.json()).id as number
}

async function detach(page: Page, eventId: number, attachmentId: number) {
    const headers = await apiHeaders(page)
    await page.request.delete(`/api/v1/events/${eventId}/attachments/${attachmentId}`, {headers})
}

test.describe('Event files', () => {
    /**
     * Attaching a file through the media browser, and marking it as kept back from the room.
     *
     * <p>The walk is the one somebody takes: open the event, pick a file out of the library, see it
     * land, and flip the switch. Nothing is saved afterwards, because an event being edited already
     * exists and a file that only lands on save is a file somebody believes they attached.
     *
     * <p>The row this story made is the one it acts on, by the id it was given. The suite runs its
     * stories side by side on the same seeded evening, so the first row on the screen is as likely
     * to belong to another story as to this one.
     */
    test('a manager hangs a library file on an event and keeps it back', async ({managerPage: page}) => {
        const eventId = await someEventId(page)
        await page.goto(`/station/events/${eventId}/edit`)

        const card = page.getByTestId('event-attachments')
        await expect(card).toBeVisible()
        const [attached] = await Promise.all([
            page.waitForResponse(response =>
                response.url().endsWith(`/events/${eventId}/attachments`)
                && response.request().method() === 'POST'),
            card.getByRole('button', {name: 'Datei anhängen'}).click()
                .then(() => page.getByTestId('media-file').filter({hasText: SEEDED_FILE}).first().click()),
        ])
        const attachmentId = (await attached.json()).id as number

        const row = page.locator(`[data-testid="event-attachment"][data-attachment="${attachmentId}"]`)
        await expect(row).toBeVisible()

        await Promise.all([
            page.waitForResponse(response =>
                response.url().endsWith(`/attachments/${attachmentId}`) && response.request().method() === 'PUT'),
            row.getByTestId('event-attachment-internal').click(),
        ])

        try {
            await page.goto(`/station/events/${eventId}`)
            const shown = page.locator(`[data-testid="event-attachment-row"][data-attachment="${attachmentId}"]`)
            await expect(shown.getByTestId('event-attachment-internal-badge')).toBeVisible()
        } finally {
            await detach(page, eventId, attachmentId)
        }
    })

    /**
     * What an event keeps back is absent for a reader without the right, not merely unmarked.
     *
     * <p>Both files hang on the same event at the same time, so the story tells an omission from an
     * empty panel: the open one is there for the member, the internal one is not, and asking for it
     * by its own address is refused rather than answered differently from the list.
     */
    test('a member is handed the open file and never the internal one', async ({
        managerPage,
        memberPage,
    }) => {
        const eventId = await someEventId(managerPage)
        const fileId = await libraryFileId(managerPage)
        const openLabel = unique('Laufzettel')
        const internalLabel = unique('Einsatzplan')
        const open = await attach(managerPage, eventId, fileId, openLabel, false)
        const internal = await attach(managerPage, eventId, fileId, internalLabel, true)

        try {
            await memberPage.goto(`/station/events/${eventId}`)
            const rows = memberPage.getByTestId('event-attachment-row')
            await expect(rows.filter({hasText: openLabel})).toBeVisible()
            await expect(rows.filter({hasText: internalLabel})).toHaveCount(0)

            const headers = await apiHeaders(memberPage)
            const refused = await memberPage.request.get(
                `/api/v1/events/${eventId}/attachments/${internal}/file`,
                {headers},
            )
            expect(refused.ok(), 'the door says what the list said').toBe(false)
        } finally {
            await detach(managerPage, eventId, open)
            await detach(managerPage, eventId, internal)
        }
    })

    /**
     * A partner station is outside the room, so it is handed the open files and nothing else.
     *
     * <p>The owning station is asked again at the source: the partner's own instance forwards the
     * request, and a file that is kept back is refused there even when its id is named, which is
     * the id the partner would have to guess since it never appears in the list.
     */
    test('a partner station is handed the open files of a shared event', async ({
        managerPage,
        browser,
        request,
    }) => {
        const readerHeaders = await apiHeaders(managerPage)
        const shared = await managerPage.request.get('/api/v1/federated/events', {headers: readerHeaders})
        expect(shared.ok(), 'the station sees what its partners share with it').toBe(true)

        const items: {partnerStationUid: string; event: {id: number}}[] = await shared.json()
        expect(items.length, 'a seeded partner shares an event with this station').toBeGreaterThan(0)
        const shown = must(items[0], 'an event a partner shares with this station')
        const owningStation = shown.partnerStationUid
        const eventId = shown.event.id

        const ownerPage = await managerOfStation(browser, request, owningStation)
        const fileId = await libraryFileId(ownerPage)
        const openLabel = unique('Laufzettel')
        const internalLabel = unique('Einsatzplan')
        const open = await attach(ownerPage, eventId, fileId, openLabel, false)
        const internal = await attach(ownerPage, eventId, fileId, internalLabel, true)

        try {
            const listed = await managerPage.request.get(
                `/api/v1/federated/${owningStation}/events/${eventId}/attachments`,
                {headers: readerHeaders},
            )
            expect(listed.ok(), await listed.text()).toBe(true)
            const names = ((await listed.json()) as {id: number; name: string}[]).map(file => file.name)
            expect(names).toContain(openLabel)
            expect(names).not.toContain(internalLabel)

            const refused = await managerPage.request.get(
                `/api/v1/federated/${owningStation}/events/${eventId}/attachments/${internal}/file`,
                {headers: readerHeaders},
            )
            expect(refused.ok(), 'the owning station refuses what it kept back').toBe(false)

            const handed = await managerPage.request.get(
                `/api/v1/federated/${owningStation}/events/${eventId}/attachments/${open}/file`,
                {headers: readerHeaders},
            )
            expect(handed.ok(), await handed.text()).toBe(true)
            expect((await handed.body()).length, 'the bytes travelled').toBeGreaterThan(0)
        } finally {
            await detach(ownerPage, eventId, open)
            await detach(ownerPage, eventId, internal)
            await ownerPage.context().close()
        }
    })
})
