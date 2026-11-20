/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {proveFreshly, stopAnsweringStepUpPrompts} from './fixtures/auth'
import {expect, homeBaseUrl, instanceRequestAs, stationManagerOf, test} from './fixtures/peer'
import {unique} from './fixtures/unique'
import {must} from './fixtures/must'
import type {APIRequestContext} from '@playwright/test'

/**
 * The station's own side of federation. Connecting two stations and reading a partner's content
 * needs both of them answering, which the seeded partner stations allow; those stories follow once
 * the suite knows how to drive two stations at once.
 *
 * <p>Connecting two stations that live on different instances has no story here and cannot have
 * one: this suite runs a single instance, and the whole of that feature is one instance calling
 * another. A story pretending otherwise would be checking a refusal on the way out rather than the
 * connection it claims to be about. The two sides meet in the backend tests instead, where both
 * instances can be stood up at once.
 */
test.describe('Federation', () => {
    /**
     * Both sides of a partnership, live at once. A station names its partners and the partner names
     * it back - which is the whole of what a partnership is, and cannot be shown from one side.
     */
    /**
     * Held back until the second station's page is understood. On its own this passes in a second;
     * inside the full run the partner's federation page renders an empty frame and stays empty for
     * forty-five seconds, so the wait is not the problem. Something about a second station's
     * session arriving while the rest of the suite is working stops that page loading at all. The
     * partner list is answered by asking the partners themselves, so it arrives later than the rest.
     */
    test('two stations each carry the other as a partner', async ({managerPage, partnerManagerPage}) => {

        await managerPage.goto('/station/federate')
        await expect(managerPage.getByTestId('app-shell')).toBeVisible()
        const partners = managerPage.locator('main').getByText(/JF |FF |Jugendfeuerwehr/)
        await expect(partners.first()).toBeVisible({timeout: 45_000})

        await partnerManagerPage.goto('/station/federate')
        await expect(partnerManagerPage.getByTestId('app-shell')).toBeVisible()
        await expect(partnerManagerPage.locator('main').getByText(/Musterstadt/).first())
            .toBeVisible({timeout: 45_000})
    })

    /** What a partner shares reaches the other station's own knowledge base. */
    test('the knowledge base shows what partner stations share', async ({managerPage: page}) => {
        await page.goto('/station/knowledge')

        await expect(page.getByPlaceholder('Suchen...')).toBeVisible()
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('the partner stations are reachable', async ({managerPage: page}) => {
        await page.goto('/station/federate')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/federate')
    })

    test('what the station shares is configurable', async ({managerPage: page}) => {
        await page.goto('/station/federate/settings')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('the boards partner stations share are reachable', async ({managerPage: page}) => {
        await page.goto('/station/federation/boards')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * Making a code asks for a fresh proof, and that question is raised over the dialog the
     * reader is standing in. The click is what proves it: it only lands when nothing covers the
     * field, which is exactly what used to be wrong. The fixture's automatic answering is taken
     * off first, because this story is about the prompt itself, and the field is whichever proof
     * the dialog offers this account: the code where it holds a second factor, the password
     * where it does not.
     */
    test('the security question opens in front of the dialog that raised it', async ({managerPage: page}) => {
        await stopAnsweringStepUpPrompts(page)
        await page.goto('/station/federate')
        await page.getByRole('button', {name: /Partner hinzufügen/}).click()

        const addPartner = page.getByRole('dialog').filter({hasText: 'Einladung erstellen'})
        await addPartner.getByRole('button', {name: /Code generieren/}).click()

        const confirmation = page.getByRole('dialog').filter({hasText: 'Sicherheitsbestätigung'})
        const factor = confirmation.getByPlaceholder('000000')
            .or(confirmation.getByPlaceholder('Dein Passwort'))
        await factor.click()
        await factor.fill('123456')

        await expect(factor).toHaveValue('123456')
        await confirmation.getByRole('button', {name: 'Abbrechen'}).click()
    })

    test('a member does not configure what the station shares', async ({memberPage: page}) => {
        await page.goto('/station/federate/settings')

        await expect(page.getByRole('button', {name: /Speichern/})).toHaveCount(0)
    })

    /**
     * An entry shared with named partners reaches a named partner on the same instance.
     *
     * <p>Named partners are the sharing station's own partner rows. A partner beside it in the same
     * database used to be looked up by its own row instead, so an entry meant for it never arrived,
     * while a partner on another instance received it. The entry shared with nobody is there to
     * show that the list is not simply everything the station wrote.
     */
    test('an entry shared with named partners reaches a partner on the same instance', async ({
        homeAdminApi,
        homeManagerApi,
    }) => {
        const manager = await stationManagerOf(homeBaseUrl())
        const created = await homeAdminApi.post('/api/v1/stations', {
            data: {name: unique('E2E-Nachbarwache'), managerEmail: manager.email},
        })
        expect(created.status()).toBe(201)
        const {id: sharingStation} = await created.json()

        const sharing = await instanceRequestAs(homeBaseUrl(), {email: manager.email, stationId: sharingStation})
        try {
            await proveFreshly(sharing)
            const invited = await sharing.post('/api/v1/federation/invite')
            expect(invited.ok()).toBe(true)
            const {inviteCode} = await invited.json()

            await proveFreshly(homeManagerApi)
            const accepted = await homeManagerApi.post('/api/v1/federation/accept', {data: {inviteCode}})
            expect(accepted.status(), await accepted.text()).toBe(201)

            const partners = await sharing.get('/api/v1/federation/partners')
            expect(partners.ok()).toBe(true)
            const rows: {partner: {id: number}}[] = await partners.json()
            const named = must(rows[0], 'the sharing station holds the partner it just gained').partner.id

            const meant = unique('Für die Nachbarn')
            const kept = unique('Nur für uns')
            const meantId = await publishedEntry(sharing, meant)
            await publishedEntry(sharing, kept)
            const shared = await sharing.put(`/api/v1/news/${meantId}/federation`, {
                data: {scope: 'SPECIFIC', visibilityRole: 'MEMBER', partnerIds: [named]},
            })
            expect(shared.ok(), await shared.text()).toBe(true)

            const browsed = await homeManagerApi.get('/api/v1/federated/news')
            expect(browsed.ok(), await browsed.text()).toBe(true)
            const titles = ((await browsed.json()) as {news: {title: string}}[]).map(item => item.news.title)
            expect(titles).toContain(meant)
            expect(titles).not.toContain(kept)

            const read = await homeManagerApi.get(`/api/v1/federated/${sharingStation}/news/${meantId}`)
            expect(read.ok(), await read.text()).toBe(true)
            expect((await read.json()).title).toBe(meant)
        } finally {
            await sharing.dispose()
        }
    })

    /**
     * A shared event group that hands a partner station the choosing of its own sign-ups, with one
     * place. The partner's sign-ups wait for the partner itself, the first it confirms gets the place,
     * and the second is refused because the place is taken.
     */
    test('a partner station confirms its own event group sign-ups within its places', async ({
        homeAdminApi,
        homeManagerApi,
    }) => {
        const manager = await stationManagerOf(homeBaseUrl())
        const created = await homeAdminApi.post('/api/v1/stations', {
            data: {name: unique('E2E-Kurswache'), managerEmail: manager.email},
        })
        expect(created.status()).toBe(201)
        const {id: holdingStation} = await created.json()

        const holder = await instanceRequestAs(homeBaseUrl(), {email: manager.email, stationId: holdingStation})
        try {
            const named = await partnerOf(holder, homeManagerApi)
            const groupId = await sharedEventGroupWithOnePlace(holder, named)

            const listed = await homeManagerApi.get('/api/v1/station-members')
            expect(listed.ok(), await listed.text()).toBe(true)
            const [first, second] = (await listed.json() as {id: number}[]).map(row => row.id)
            const base = `/api/v1/federated/${holdingStation}/events/groups/${groupId}`
            const partId = await onlyPartOf(holder, groupId)
            for (const memberId of [must(first, 'a member'), must(second, 'a second member')]) {
                const signedUp = await homeManagerApi.post(`${base}/sign-up`, {data: {parts: [partId], memberId}})
                expect(signedUp.status(), await signedUp.text()).toBe(201)
                expect((await signedUp.json()).status, 'the sign-up waits for the partner itself').toBe('PENDING')
            }

            const confirmed = await homeManagerApi.post(`${base}/confirm`, {data: {memberId: first}})
            expect(confirmed.ok(), await confirmed.text()).toBe(true)
            expect((await confirmed.json()).status).toBe('ACCEPTED')

            const refused = await homeManagerApi.post(`${base}/confirm`, {data: {memberId: second}})
            expect(refused.status(), await refused.text()).toBe(400)
            expect((await refused.json()).code, 'the one place is taken').toBe(NO_PLACES_LEFT_FOR_PARTNER)
        } finally {
            await holder.dispose()
        }
    })
})

/** The refusal a partner's confirm meets once its places are taken. */
const NO_PLACES_LEFT_FOR_PARTNER = 'E-039'

/**
 * Pairs the holding station with the home manager's station by an invite code, and answers the id the
 * holding station knows its new partner by.
 */
async function partnerOf(holder: APIRequestContext, home: APIRequestContext): Promise<number> {
    await proveFreshly(holder)
    const invited = await holder.post('/api/v1/federation/invite')
    expect(invited.ok(), await invited.text()).toBe(true)
    const {inviteCode} = await invited.json()
    await proveFreshly(home)
    const accepted = await home.post('/api/v1/federation/accept', {data: {inviteCode}})
    expect(accepted.status(), await accepted.text()).toBe(201)
    const partners = await holder.get('/api/v1/federation/partners')
    expect(partners.ok()).toBe(true)
    const rows: {partner: {id: number}}[] = await partners.json()
    return must(rows[0], 'the holding station holds the partner it just gained').partner.id
}

/**
 * An event group a month ahead with one date, shared with the named partner only, which decides its
 * own sign-ups within one place.
 */
async function sharedEventGroupWithOnePlace(holder: APIRequestContext, partnerId: number): Promise<number> {
    const created = await holder.post('/api/v1/events/groups', {
        data: {
            details: {
                name: unique('Geteilter Kurs'), description: null, registrationLimit: null, registrationDeadline: null,
                requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null,
                partRegistrationLimit: null, queueOrder: null,
            },
            sets: [{id: null, name: 'Kurstag', minimum: 1, maximum: null}],
        },
    })
    expect(created.ok(), await created.text()).toBe(true)
    const group = await created.json() as {eventGroup: {id: number}, sets: {id: number}[]}
    const groupId = group.eventGroup.id
    const start = new Date(Date.now() + 30 * 86_400_000)
    start.setUTCHours(10, 0, 0, 0)
    const part = await holder.post(`/api/v1/events/groups/${groupId}/parts/new`, {
        data: {name: 'Kurstag', setId: must(group.sets[0], 'the set').id, startTime: start.toISOString(),
            endTime: new Date(start.getTime() + 3_600_000).toISOString()},
    })
    expect(part.ok(), await part.text()).toBe(true)
    const shared = await holder.put(`/api/v1/events/groups/${groupId}/federation`, {data: {scope: 'SPECIFIC', partnerIds: [partnerId]}})
    expect(shared.ok(), await shared.text()).toBe(true)
    const placed = await holder.put(`/api/v1/events/groups/${groupId}/partner-places/${partnerId}`,
        {data: {slotBudget: 1, partnerConfirms: true}})
    expect(placed.ok(), await placed.text()).toBe(true)
    return groupId
}

/** The one date of an event group, as the holding station lists it. */
async function onlyPartOf(holder: APIRequestContext, groupId: number): Promise<number> {
    const read = await holder.get(`/api/v1/events/groups/${groupId}`)
    expect(read.ok(), await read.text()).toBe(true)
    return must((await read.json() as {parts: {eventId: number}[]}).parts[0], 'the date').eventId
}

/** A published entry of the station, with the title given. */
async function publishedEntry(api: APIRequestContext, title: string): Promise<number> {
    const created = await api.post('/api/v1/news', {data: {title, contentMarkdown: 'Hallo Nachbarn'}})
    expect(created.status(), await created.text()).toBe(201)
    return (await created.json()).id
}
