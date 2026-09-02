/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Page} from '@playwright/test'
import type {DemoAccount} from './fixtures/auth'
import {test, expect, apiHeaders, demoAccounts, otherStationManager, pageAsThrowaway, stationPeers} from './fixtures/auth'
import {sidebarEntry} from './fixtures/sidebar'
import {unique} from './fixtures/unique'

/**
 * The lost and found, end to end: reporting a find, giving it a picture, claiming it, taking that
 * claim back, handing it over and clearing it away, plus who may see and reach what.
 *
 * The stories run one after another. What waits in the lost and found is counted for the whole
 * station and shown in the sidebar, so two stories claiming and handing over at the same moment
 * would each be reading the other's arithmetic.
 */
test.describe.configure({mode: 'serial'})

/** A real picture, small enough to write out here: a single opaque pixel. */
const ONE_PIXEL_PNG = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    'base64',
)

const LIST = '/station/lost-and-found'

/** The dialog's submit carries the same label as the button that opens it, so the last is taken. */
async function report(page: Page, description: string, options?: {image?: boolean; foundAt?: string}) {
    await page.goto(LIST)
    await page.getByRole('button', {name: 'Fundgegenstand melden'}).click()

    const dialog = page.getByRole('dialog')
    if (options?.image) {
        await dialog.locator('input[type="file"]').first().setInputFiles({
            name: 'fund.png',
            mimeType: 'image/png',
            buffer: ONE_PIXEL_PNG,
        })
    }
    if (description) await dialog.getByRole('textbox').first().fill(description)
    if (options?.foundAt) await dialog.locator('input[type="date"]').fill(options.foundAt)

    await page.getByRole('button', {name: 'Fundgegenstand melden'}).last().click()
}

/**
 * The card carrying the given words, which is what every assertion below is made against. Asked
 * for by what a card is rather than by a div holding the words, because every box around it holds
 * them too and the innermost one is the paragraph, which carries none of the buttons.
 */
function card(page: Page, description: string) {
    return page.getByTestId('lost-item-card').filter({hasText: description})
}

/** What the station currently offers, read as the person whose page this is. */
async function items(page: Page): Promise<
    {id: number; description: string | null; claimedBy: number | null; hasImage: boolean}[]> {
    const response = await page.request.get('/api/v1/lost-and-found', {headers: await apiHeaders(page)})
    expect(response.ok(), 'the lost and found can be listed').toBeTruthy()
    return response.json()
}

/** Files a find straight through the endpoint, for the stories that are not about the dialog. */
async function reportThroughApi(page: Page, description: string): Promise<number> {
    const response = await page.request.post('/api/v1/lost-and-found', {
        headers: await apiHeaders(page),
        data: {description},
    })
    expect(response.status(), 'a find is filed').toBe(201)
    return (await response.json()).id
}

/**
 * A freshly filed find that really carries no picture.
 *
 * The run before this one is emptied out of the database but not out of the file store, so a new
 * entry can land on a number an earlier run left a picture under. A story about giving an entry its
 * first picture has to start from one that has none, so it files again until it gets one.
 */
async function reportWithoutPicture(page: Page, description: string): Promise<number> {
    const headers = await apiHeaders(page)
    for (let attempt = 0; attempt < 5; attempt++) {
        const id = await reportThroughApi(page, description)
        if (!(await items(page)).find(entry => entry.id === id)?.hasImage) return id
        await page.request.delete(`/api/v1/lost-and-found/${id}`, {headers})
    }
    throw new Error('Every number filed under carried a picture left behind by an earlier run')
}

/**
 * Somebody who looks after a member, at the station the shared manager acts for. A guardian from
 * another station would be signed in at a lost and found that never sees the find this story files.
 */
async function guardianOfTheStation(request: APIRequestContext): Promise<DemoAccount> {
    const {manager} = await stationPeers(request)
    const accounts = await demoAccounts(request)
    const guardian = accounts.find(account => !!account.email
        && account.stationId === manager.stationId
        && account.permissions.includes('MEMBER_GUARDIAN'))
    if (!guardian) throw new Error('The station the stories act on has nobody looking after a member')
    return guardian
}

/** How many claimed items the sidebar says are waiting to be handed over. */
async function waitingCount(page: Page): Promise<number> {
    const response = await page.request.get('/api/v1/sidebar-counts', {headers: await apiHeaders(page)})
    expect(response.ok(), 'the sidebar counts can be read').toBeTruthy()
    return (await response.json()).lostAndFoundPending
}

test.describe('Lost and found', () => {
    test('a found item is reported and appears in the list', async ({managerPage: page}) => {
        const item = unique('Fundstueck')

        await report(page, item)

        await expect(page.getByText(item).first()).toBeVisible()
    })

    /**
     * A description is optional, and the card has to stand for itself without one: an entry
     * reading as an empty box is an entry nobody recognises their glove in.
     */
    test('a find without a description says so rather than showing nothing', async ({managerPage: page}) => {
        const before = (await items(page)).length

        await report(page, '')

        await expect(page.getByText('Keine Beschreibung').first()).toBeVisible()
        expect((await items(page)).length, 'exactly one entry was filed').toBe(before + 1)
    })

    test('a find carries the picture it was reported with', async ({managerPage: page}) => {
        const item = unique('Fundstueck-Bild')

        await report(page, item, {image: true})

        await expect(card(page, item).locator('img')).toBeVisible()
    })

    /**
     * The date the thing was found is not the date it was written down: somebody clearing the
     * cupboard on Monday enters what they know about last Friday.
     */
    test('a find keeps the date it was given rather than today', async ({managerPage: page}) => {
        const item = unique('Fundstueck-Datum')

        await report(page, item, {foundAt: '2026-05-18'})

        await expect(card(page, item).getByText('18.05.2026')).toBeVisible()
    })

    /**
     * The story the report came from. Filing a find is two requests and the picture is the second,
     * and a failing picture used to leave the dialog open on a form that had already been filed:
     * pressing again wrote a second entry beside the first. Exactly one entry has to stand
     * afterwards, and pressing again may only send the picture.
     */
    test('a picture that will not upload does not file the same find twice', async ({managerPage: page}) => {
        const item = unique('Fundstueck-Zweimal')
        const before = (await items(page)).length

        let refuse = true
        await page.route('**/api/v1/lost-and-found/*/image', async route => {
            if (!refuse) return route.continue()
            await route.fulfill({status: 400, json: {message: 'Bild abgelehnt'}})
        })

        await report(page, item, {image: true})

        const dialog = page.getByRole('dialog')
        await expect(dialog.getByTestId('create-error')).toContainText('Bild abgelehnt')
        await expect(dialog.getByTestId('saved-without-image')).toBeVisible()
        expect((await items(page)).length, 'the find was written down once').toBe(before + 1)

        refuse = false
        await page.getByRole('button', {name: 'Bild erneut senden'}).click()

        await expect(dialog).toBeHidden()
        expect((await items(page)).length, 'trying again attaches to the same entry').toBe(before + 1)
        await expect(card(page, item).locator('img')).toBeVisible()
    })

    /**
     * A file that is not a picture never reaches the server: the browser has to read it to make it
     * smaller, and what it cannot read it says so about, rather than paying for an upload that ends
     * in a refusal nobody can act on.
     */
    test('a file that is not a picture is refused with a reason', async ({managerPage: page}) => {
        const item = unique('Fundstueck-Format')

        await page.goto(LIST)
        await page.getByRole('button', {name: 'Fundgegenstand melden'}).click()

        const dialog = page.getByRole('dialog')
        await dialog.locator('input[type="file"]').first().setInputFiles({
            name: 'notizen.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from('Das ist kein Bild.'),
        })
        await dialog.getByRole('textbox').first().fill(item)
        await page.getByRole('button', {name: 'Fundgegenstand melden'}).last().click()

        await expect(dialog.getByTestId('create-error')).toContainText('nicht gelesen werden')
    })

    /** The endpoint keeps its own guard, because a screen is not what stops a request. */
    test('the endpoint refuses a picture in a format it does not keep', async ({managerPage: page}) => {
        const id = await reportThroughApi(page, unique('Fundstueck-Endpunkt'))

        const response = await page.request.post(`/api/v1/lost-and-found/${id}/image`, {
            headers: await apiHeaders(page),
            multipart: {image: {name: 'x.gif', mimeType: 'image/gif', buffer: ONE_PIXEL_PNG}},
        })

        expect(response.status(), 'a format the server does not keep is refused').toBe(400)
        expect(await response.text()).toContain('PNG')
    })

    /**
     * A picture is served in the size it is asked for and in the one it came in, and both have to
     * come back as pictures rather than as the page the browser gets when it is not signed in.
     */
    test('a picture is served whole and in a smaller size', async ({managerPage: page}) => {
        const id = await reportThroughApi(page, unique('Fundstueck-Groessen'))
        const headers = await apiHeaders(page)

        const upload = await page.request.post(`/api/v1/lost-and-found/${id}/image`, {
            headers,
            multipart: {image: {name: 'fund.png', mimeType: 'image/png', buffer: ONE_PIXEL_PNG}},
        })
        expect(upload.ok(), 'the picture is taken').toBeTruthy()

        for (const query of ['', '?size=300']) {
            const image = await page.request.get(`/api/v1/lost-and-found/${id}/image${query}`, {headers})
            expect(image.ok(), `the picture answers for ${query || 'the original'}`).toBeTruthy()
            expect(image.headers()['content-type']).toContain('image/')
        }

        const missing = await page.request.get('/api/v1/lost-and-found/999999/image', {headers})
        expect(missing.status(), 'an item nobody has answers as missing').toBe(404)
    })

    /** A picture can be given to an entry that was filed without one. */
    test('a picture is added to an entry afterwards', async ({managerPage: page}) => {
        const item = unique('Fundstueck-Nachreichen')
        await reportWithoutPicture(page, item)

        await page.goto(LIST)
        await card(page, item).getByRole('button', {name: 'Bild nachreichen'}).click()
        await page.locator('input[type="file"]').last().setInputFiles({
            name: 'fund.png',
            mimeType: 'image/png',
            buffer: ONE_PIXEL_PNG,
        })

        await expect(card(page, item).locator('img')).toBeVisible()
    })

    test('a member claims a find for themselves', async ({managerPage: page}) => {
        const item = unique('Fundstueck-Meins')
        await reportThroughApi(page, item)

        await page.goto(LIST)
        await card(page, item).getByRole('button', {name: 'Gehört mir'}).click()
        await page.getByRole('dialog').getByRole('button', {name: 'Gehört mir'}).click()

        await expect(card(page, item).getByText('Von dir beansprucht')).toBeVisible()
    })

    /**
     * A claim made by mistake used to stand for good: the only ways out were handing the thing over
     * or deleting it altogether. Taking it back has to leave the entry standing and free again.
     */
    test('a claim is taken back and the find is free again', async ({managerPage: page}) => {
        const item = unique('Fundstueck-Zurueck')
        await reportThroughApi(page, item)

        await page.goto(LIST)
        await card(page, item).getByRole('button', {name: 'Gehört mir'}).click()
        await page.getByRole('dialog').getByRole('button', {name: 'Gehört mir'}).click()
        await expect(card(page, item).getByText('Von dir beansprucht')).toBeVisible()

        await card(page, item).getByRole('button', {name: 'Anspruch zurücknehmen'}).click()
        await page.getByRole('dialog').getByRole('button', {name: 'Anspruch zurücknehmen'}).click()

        await expect(card(page, item).getByRole('button', {name: 'Gehört mir'})).toBeVisible()
        await expect(card(page, item).getByText('Von dir beansprucht')).toHaveCount(0)
    })

    /**
     * What waits to be handed over is counted for the whole station and shown beside the sidebar
     * entry. It has to follow a claim and follow the handover back down again.
     */
    test('the sidebar counts what waits to be handed over', async ({managerPage: page}) => {
        const item = unique('Fundstueck-Zaehler')
        const id = await reportThroughApi(page, item)
        const before = await waitingCount(page)

        await page.goto(LIST)
        await card(page, item).getByRole('button', {name: 'Gehört mir'}).click()
        await page.getByRole('dialog').getByRole('button', {name: 'Gehört mir'}).click()
        await expect(card(page, item).getByText('Von dir beansprucht')).toBeVisible()

        expect(await waitingCount(page), 'a claim raises the count').toBe(before + 1)
        await expect(sidebarEntry(page, 'Fundbüro')).toContainText(String(before + 1))

        await card(page, item).getByRole('button', {name: 'Ausgegeben'}).click()
        await page.getByRole('dialog').getByRole('button', {name: 'Ausgegeben'}).click()
        await expect(page.getByText(item)).toHaveCount(0)

        expect(await waitingCount(page), 'handing it over lowers it again').toBe(before)
        expect((await items(page)).some(i => i.id === id), 'the entry is gone').toBeFalsy()
    })

    test('the management deletes a find', async ({managerPage: page}) => {
        const item = unique('Fundstueck-Weg')
        const id = await reportThroughApi(page, item)

        await page.goto(LIST)
        await card(page, item).getByRole('button', {name: 'Löschen'}).click()

        await expect(page.getByText(item)).toHaveCount(0)
        expect((await items(page)).some(i => i.id === id), 'the entry is gone').toBeFalsy()
    })

    /**
     * Two people at the same station at the same time, because that is the only way to tell what a
     * role sees from what a role is told it sees. Whoever looks after the lost and found sees every
     * entry; everybody else sees what is still free and what they claimed themselves.
     */
    test('an ordinary member sees only what is free and what is theirs', async ({browser, request, managerPage}) => {
        const {member} = await stationPeers(request)
        const memberPage = await pageAsThrowaway(browser, request, [], member)
        const mine = unique('Fundstueck-Meins-Sicht')
        const theirs = unique('Fundstueck-Fremd')

        try {
            await reportThroughApi(managerPage, mine)
            const theirsId = await reportThroughApi(managerPage, theirs)

            await memberPage.goto(LIST)
            await card(memberPage, mine)
                .getByRole('button', {name: 'Gehört mir'}).click()
            await memberPage.getByRole('dialog').getByRole('button', {name: 'Gehört mir'}).click()
            await expect(memberPage.getByText('Von dir beansprucht').first()).toBeVisible()

            const claimed = await managerPage.request.post(`/api/v1/lost-and-found/${theirsId}/claim`, {
                headers: await apiHeaders(managerPage),
                data: {},
            })
            expect(claimed.ok(), 'the management claims the other entry').toBeTruthy()

            await memberPage.reload()
            await expect(memberPage.getByText(mine)).toHaveCount(1)
            await expect(memberPage.getByText(theirs), 'somebody else\'s claim is not theirs to see')
                .toHaveCount(0)

            await managerPage.goto(LIST)
            await expect(managerPage.getByText(theirs).first(), 'the management sees every entry').toBeVisible()
            await expect(managerPage.getByText(mine).first()).toBeVisible()
        } finally {
            await memberPage.context().close()
        }
    })

    /**
     * A parent collecting a glove for their child. The claim has to land on the child, which is
     * what the picker in the dialog is for, and the entry has to say the child's name afterwards.
     */
    test('a guardian claims a find for somebody in their care', async ({browser, request, managerPage}) => {
        const guardianPage = await pageAsThrowaway(browser, request, [], await guardianOfTheStation(request))
        const item = unique('Fundstueck-Kind')

        try {
            const managed = await guardianPage.request.get('/api/v1/managed-members', {
                headers: await apiHeaders(guardianPage),
            })
            expect(managed.ok(), 'the guardian can list who they look after').toBeTruthy()
            const child = (await managed.json())[0]
            expect(child, 'the seeded guardian looks after somebody').toBeTruthy()

            await reportThroughApi(managerPage, item)

            await guardianPage.goto(LIST)
            await card(guardianPage, item)
                .getByRole('button', {name: 'Gehört mir'}).click()
            const dialog = guardianPage.getByRole('dialog')
            await dialog.getByTestId('claim-for').selectOption({label: child.name})
            await dialog.getByRole('button', {name: 'Gehört mir'}).click()

            await expect(guardianPage.getByText(`Beansprucht von ${child.name}`).first()).toBeVisible()

            await card(guardianPage, item)
                .getByRole('button', {name: 'Anspruch zurücknehmen'}).click()
            await dialog.getByRole('button', {name: 'Anspruch zurücknehmen'}).click()
            await expect(card(guardianPage, item)
                .getByRole('button', {name: 'Gehört mir'})).toBeVisible()
        } finally {
            await guardianPage.context().close()
        }
    })

    /**
     * A find belongs to the station that keeps it. Its number must reach nothing from anywhere
     * else: not the entry, not its picture, and neither by claiming it nor by giving it a picture.
     */
    test('a find of another station cannot be reached through its number', async ({browser, request, managerPage}) => {
        const {manager} = await stationPeers(request)
        const other = await otherStationManager(request, manager.stationId, manager.email)
        const otherPage = await pageAsThrowaway(browser, request, [], other)

        try {
            const id = await reportThroughApi(managerPage, unique('Fundstueck-Fremde-Wache'))
            const headers = await apiHeaders(otherPage)

            const read = await otherPage.request.get(`/api/v1/lost-and-found/${id}`, {headers})
            expect(read.status(), 'the entry is not theirs to read').toBe(404)

            const image = await otherPage.request.get(`/api/v1/lost-and-found/${id}/image`, {headers})
            expect(image.status(), 'nor is its picture').toBe(404)

            const claim = await otherPage.request.post(`/api/v1/lost-and-found/${id}/claim`, {headers, data: {}})
            expect(claim.status(), 'nor may they claim it').toBe(404)

            const upload = await otherPage.request.post(`/api/v1/lost-and-found/${id}/image`, {
                headers,
                multipart: {image: {name: 'fund.png', mimeType: 'image/png', buffer: ONE_PIXEL_PNG}},
            })
            expect(upload.status(), 'nor may they give it one').toBe(404)

            expect((await items(otherPage)).some(i => i.id === id), 'it is not in their list').toBeFalsy()
        } finally {
            await otherPage.context().close()
        }
    })

    /**
     * Reporting tells the station and claiming tells the people who look after the lost and found.
     * The notice is followed by pressing it rather than by reading its address, because a link
     * naming a page the application does not have goes nowhere only when somebody actually presses
     * it. Claiming then withdraws the announcement about that one find and leaves the rest standing.
     */
    test('reporting and claiming tell the right people, and the notice leads to a page',
        async ({browser, request, managerPage}) => {
            const {member} = await stationPeers(request)
            const memberPage = await pageAsThrowaway(browser, request, [], member)
            const followed = unique('Fundstueck-Nachricht')
            const claimedItem = unique('Fundstueck-Beansprucht')
            const untouched = unique('Fundstueck-Unberuehrt')

            try {
                const followedId = await reportThroughApi(managerPage, followed)
                const claimedId = await reportThroughApi(managerPage, claimedItem)
                const untouchedId = await reportThroughApi(managerPage, untouched)

                const announced = await notices(memberPage, 'LOST_AND_FOUND_NEW', followedId)
                expect(announced, 'the station hears about a find').toBeTruthy()
                expect(announced.link.route, 'the notice names a page').toBe('lost-and-found')

                await memberPage.goto('/station/dashboard/overview')
                await memberPage.getByText(followed).first().click()
                await expect(memberPage).toHaveURL(new RegExp(`${LIST}$`))
                await expect(card(memberPage, followed)).toBeVisible()

                await card(memberPage, claimedItem)
                    .getByRole('button', {name: 'Gehört mir'}).click()
                await memberPage.getByRole('dialog').getByRole('button', {name: 'Gehört mir'}).click()
                await expect(memberPage.getByText('Von dir beansprucht').first()).toBeVisible()

                const claimNotice = await notices(managerPage, 'LOST_AND_FOUND_CLAIMED', claimedId)
                expect(claimNotice, 'whoever runs the station hears about the claim').toBeTruthy()
                expect(claimNotice.link.route, 'that notice names a page too').toBe('lost-and-found')

                expect(await notices(memberPage, 'LOST_AND_FOUND_NEW', claimedId),
                    'the announcement is withdrawn once the thing is spoken for').toBeUndefined()
                expect(await notices(memberPage, 'LOST_AND_FOUND_NEW', untouchedId),
                    'and only that one, not every other find along with it').toBeTruthy()
            } finally {
                await memberPage.context().close()
            }
        })

    /**
     * A find that has been handed over takes its notices with it, so nothing is left pointing at an
     * entry that no longer exists.
     */
    test('handing a find over withdraws the notices about it', async ({browser, request, managerPage}) => {
        const {member} = await stationPeers(request)
        const memberPage = await pageAsThrowaway(browser, request, [], member)
        const item = unique('Fundstueck-Aufgeraeumt')

        try {
            const id = await reportThroughApi(managerPage, item)
            expect(await notices(memberPage, 'LOST_AND_FOUND_NEW', id)).toBeTruthy()

            const headers = await apiHeaders(managerPage)
            await managerPage.request.delete(`/api/v1/lost-and-found/${id}`, {headers})

            expect(await notices(memberPage, 'LOST_AND_FOUND_NEW', id),
                'nothing is left pointing at an entry that is gone').toBeUndefined()
        } finally {
            await memberPage.context().close()
        }
    })

    /**
     * A station that has switched the lost and found off stops offering it. Acted out on the second
     * seeded station, the one the module stories already use, because a station missing a module for
     * a moment is a station every other story would trip over.
     */
    test('switching the module off takes the lost and found out of the station',
        async ({partnerManagerPage: page}) => {
            await page.goto(LIST)
            await expect(sidebarEntry(page, 'Fundbüro')).toBeVisible()
            await expect(page.getByRole('button', {name: 'Fundgegenstand melden'})).toBeVisible()

            await setLostAndFound(page, false)
            try {
                await page.goto('/station/dashboard/overview')
                await expect(sidebarEntry(page, 'Fundbüro')).toHaveCount(0)
            } finally {
                await setLostAndFound(page, true)
            }

            await page.goto(LIST)
            await expect(sidebarEntry(page, 'Fundbüro')).toBeVisible()
        })
})

/** The unread notice of a kind that points at one particular find, or nothing where there is none. */
async function notices(page: Page, type: string, itemId: number) {
    const response = await page.request.get('/api/v1/notifications/unacknowledged', {
        headers: await apiHeaders(page),
    })
    expect(response.ok(), 'the notices can be read').toBeTruthy()
    return (await response.json()).find((entry: {type: string; link?: {routeParams?: {id?: number}}}) =>
        entry.type === type && String(entry.link?.routeParams?.id) === String(itemId))
}

/** Flips the lost and found for the station whose page this is, waiting for the switch to settle. */
async function setLostAndFound(page: Page, enabled: boolean): Promise<void> {
    await page.goto('/station/manage/modules')
    const toggle = page.locator('[data-testid="module-toggle"][data-module="LOST_AND_FOUND"]').getByRole('switch')
    await expect(toggle).toHaveAttribute('aria-checked', String(!enabled))
    await toggle.click()
    await expect(toggle).toHaveAttribute('aria-checked', String(enabled))
}
