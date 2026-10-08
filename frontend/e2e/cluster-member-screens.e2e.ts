/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, clusterAccountWith, clusterHeaders, clusterPage, theSeededCluster} from './fixtures/auth'
import {ownCluster} from './fixtures/cluster'
import {must} from './fixtures/must'
import {demoSignIn, sessionHeaders} from './fixtures/session'
import type {Browser} from '@playwright/test'

/**
 * Signs in as the account behind the address and accepts the request the named station sent it.
 *
 * @param browser     to open the person's own context with
 * @param email       the address the account signs in with
 * @param stationName the station whose request is accepted
 */
async function acceptLinkRequest(browser: Browser, email: string, stationName: string): Promise<void> {
    const context = await browser.newContext()
    const session = await demoSignIn(context.request, email)
    const headers = sessionHeaders(session)
    const waiting = await context.request.get('/api/v1/account/link-requests', {headers})
    expect(waiting.ok(), `the person sees what waits for them (${await waiting.text()})`).toBeTruthy()
    const prompt = must((await waiting.json() as {uid: string; stationName: string}[])
        .find(candidate => candidate.stationName === stationName), `the request of ${stationName}`)
    const accepted = await context.request.post(`/api/v1/account/link-requests/${prompt.uid}/accept`, {headers})
    expect(accepted.ok(), `the person accepted (${await accepted.text()})`).toBeTruthy()
    await context.close()
}

/**
 * The two lists an association keeps of people, and the screen behind one of them.
 *
 * The people who run the association and the people at its stations were both called members and both
 * shown as a stack of rows. They are different things and are now shown differently, which is what
 * these stories walk.
 */
test.describe('Cluster member screens', () => {
    /**
     * CLS-63 - The people at the stations are shown in the station's own table.
     *
     * Search, sortable columns and a column picker, none of which the old stack of rows had. The
     * station column is the one thing the station's own list never needs.
     */
    test('the association browses station members in a real table', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(page.getByTestId('member-row').first()).toBeVisible({timeout: 15000})

        await expect(page.locator('select').first(), 'narrowing to one station is offered').toBeVisible()
        await page.context().close()
    })

    /**
     * CLS-96 - The people at the stations are shown as people.
     *
     * CLS-63 asserted the rows existed, and a row with an empty name is still a row. The search
     * handed the browser a name nothing reads and a null identity, which is what every list draws a
     * person from, so every line carried a blank space beside a blank avatar.
     *
     * Read off the rows rather than looked up one by one: the list is live and other stories take people
     * on while this reads it, so which people are on screen is not what this is about.
     */
    test('every row on the station member list carries a name', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)
        const cluster = await theSeededCluster(page)
        const headers = await clusterHeaders(page, cluster)

        const found = await page.request
            .get('/api/v1/cluster/members/manage/search?size=200', {headers})
            .then(r => r.json())
        expect(found.members.length, 'the association has people at its stations').toBeGreaterThan(0)
        expect(found.members.every((row: {identity: unknown}) => !!row.identity),
            'and the search says who each of them is').toBeTruthy()

        await page.goto('/cluster/members')
        await expect(page.getByTestId('member-row').first()).toBeVisible({timeout: 15000})

        const drawn = await page.getByTestId('member-row').getByTestId('member-name').allInnerTexts()
        expect(drawn.length, 'the list has rows').toBeGreaterThan(0)
        expect(drawn.filter(text => text.trim().length === 0),
            'not one of them is a blank space beside a blank avatar').toEqual([])

        await page.context().close()
    })

    /**
     * CLS-97 - The station is named only while it is worth naming, and then all of them are.
     *
     * The note was drawn unconditionally, so narrowing to one station left every row repeating the same
     * word. And the search returns one row per membership, so somebody at two stations of the
     * association was two rows that never said they were the same person.
     *
     * Taking somebody on whose address already has an account only asks that account to be linked, so
     * the person accepts the second station's request before both rows are the same person.
     *
     * The narrowing is retried until the screen says so rather than until the box holds the value: the
     * page is server rendered, and a change fired before Vue is listening sets the box and nothing else.
     * Each attempt goes back through "every station" first, because picking a value the box already
     * holds fires nothing at all and the retry would then be no retry.
     */
    test('the station note is silent under a filter and names every station otherwise',
        async ({adminPage: page, browser, request}) => {
            const own = await ownCluster(page, browser, request, 'ZweiWachen')
            const second = await page.request.post('/api/v1/cluster/stations',
                {headers: own.headers, data: {name: `${own.name} Zweite`}})
            expect(second.ok(), `the association made a second station (${await second.text()})`).toBeTruthy()
            const secondUid = (await second.json()).uid

            const surname = `Doppelt${Date.now()}`
            const email = `${surname.toLowerCase()}@e2e.ember`
            for (const stationUid of [own.stationUid, secondUid]) {
                const taken = await page.request.post(
                    `/api/v1/cluster/members/manage/stations/${stationUid}/members`,
                    {headers: own.headers, data: {firstName: 'Erika', lastName: surname, email}})
                expect(taken.ok(), `they were taken on (${await taken.text()})`).toBeTruthy()
            }
            await acceptLinkRequest(browser, email, `${own.name} Zweite`)

            await page.goto('/cluster/members')
            await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), own.uid)
            await page.goto('/cluster/members')
            await expect(page.getByTestId('app-shell')).toBeVisible()

            const rows = page.getByTestId('member-row').filter({hasText: surname})
            await expect(rows).toHaveCount(2, {timeout: 15000})
            const note = rows.first().getByTestId('member-note')
            await expect(note).toContainText(own.stationName)
            await expect(note, 'and the note says both, not only the station the row came from')
                .toContainText(`${own.name} Zweite`)

            const stationFilter = page.locator('select').first()
            await expect(async () => {
                await stationFilter.selectOption('')
                await stationFilter.selectOption({label: own.stationName})
                await expect(rows, 'one station in view leaves one of the two memberships')
                    .toHaveCount(1, {timeout: 5000})
                await expect(rows.first().getByTestId('member-note'),
                    'and the note goes silent once there is only one station to name')
                    .toHaveCount(0, {timeout: 5000})
            }).toPass({timeout: 30000})

            await own.stationPage.context().close()
        })

    /**
     * CLS-64 - A station's own questions are not offered as columns.
     *
     * Stations declare their own, so a union would offer a picker where most columns are empty for
     * most rows. Only what the association asks of everybody can honestly be a column.
     */
    test('no station-local column is offered across the stations', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members')
        await expect(page.getByTestId('member-row').first()).toBeVisible({timeout: 15000})

        await expect(page.getByRole('columnheader', {name: /Gruppen/i}), 'groups and tags belong to one station')
            .toHaveCount(0)
        await expect(page.getByRole('columnheader', {name: /Tags/i})).toHaveCount(0)
        await page.context().close()
    })

    /**
     * CLS-65 - The roster opens on the roster.
     *
     * The old screen opened on a form to add somebody, with the people below it. The people come
     * first now and adding is a dialog.
     */
    test('the association team opens on its people', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_ADMINISTRATOR')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/team')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(page.getByTestId('roster-row').first()).toBeVisible({timeout: 15000})

        await expect(page.getByPlaceholder(/@/), 'adding is behind a button').toHaveCount(0)
        await page.context().close()
    })

    /**
     * CLS-98 - The association takes on somebody Ember has never seen.
     *
     * The roster refused every address without an account behind it, so the one body that cannot walk
     * up to a person and hand them a login was the association. It asks the address first, as it always
     * did, and nothing beyond it until the server has said nobody has that address.
     */
    test('the association takes on somebody with no account yet', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_ADMINISTRATOR')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/team')
        await expect(page.getByTestId('roster-row').first()).toBeVisible({timeout: 15000})

        const surname = `Unbekannt${Date.now()}`
        await page.getByRole('button', {name: /Aufnehmen/i}).first().click()
        await page.getByTestId('cluster-roster-email').fill(`${surname.toLowerCase()}@e2e.ember`)

        await expect(page.getByTestId('cluster-roster-first')).toHaveCount(0)
        await page.getByTestId('cluster-roster-add').click()

        await expect(page.getByTestId('cluster-roster-first')).toBeVisible({timeout: 15000})
        await page.getByTestId('cluster-roster-first').fill('Erika')
        await page.getByTestId('cluster-roster-last').fill(surname)
        await page.getByTestId('cluster-roster-add').click()

        await expect(page.getByTestId('roster-row').filter({hasText: surname}).first())
            .toBeVisible({timeout: 15000})
        await page.context().close()
    })

    /**
     * CLS-66 - Picking somebody shows where each of their rights comes from.
     *
     * Type, their own grants and their groups are three different sources, and the resolved set is
     * what they come to together.
     */
    test('a person on the team shows their rights and where they came from', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_ADMINISTRATOR')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/team')
        await page.getByTestId('roster-row').first().click()

        await expect(page.getByText(/Rechte|Gruppen/).first()).toBeVisible({timeout: 15000})
        await page.context().close()
    })

    /**
     * CLS-67 - Opening somebody shows what is asked of them, from both sides at once.
     *
     * The station's questions and the association's in one form. This screen could not exist until
     * the endpoints behind it were written, which is the whole reason it is here.
     */
    test('the association answers the questions asked of one person', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members')
        const row = page.getByTestId('member-row').first()
        await expect(row).toBeVisible({timeout: 15000})
        await row.getByRole('button').first().click()

        await expect(page).toHaveURL(/\/cluster\/members\/\d+$/)
        await expect(page.getByRole('heading', {name: 'Angaben', exact: true})).toBeVisible({timeout: 15000})
        await page.context().close()
    })

    /**
     * CLS-68 - The old addresses still work.
     *
     * Members came to mean the people at the stations, so the list moved up an address and the roster
     * moved out to one of its own.
     */
    test('the renamed member screens keep their old addresses working', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members/manage')
        await expect(page).toHaveURL(/\/cluster\/members$/)

        await page.context().close()
    })

    /**
     * CLS-70 - The association takes somebody on, naming the station first.
     *
     * A member belongs to a station and the association is standing in for one, so the station is the
     * first thing asked rather than something inferred. The association's rights do not become station
     * rights anywhere but its own station, which is why this goes through the association's own route
     * rather than mounting the station's create screen.
     */
    test('the association takes somebody on at one of its stations', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members')
        await expect(page.getByTestId('member-row').first()).toBeVisible({timeout: 15000})

        await page.getByTestId('cluster-member-create').click()
        await expect(page.getByTestId('cluster-member-create-modal')).toBeVisible()

        const options = page.getByTestId('cluster-member-create-station').locator('option')
        const stationUid = await options.nth(1).getAttribute('value')
        expect(stationUid, 'the association has a station to take somebody on at').toBeTruthy()
        await page.getByTestId('cluster-member-create-station').selectOption(stationUid!)

        const surname = `Neuzugang${Date.now()}`
        await page.getByTestId('cluster-member-create-first').fill('Erika')
        await page.getByTestId('cluster-member-create-last').fill(surname)
        await page.getByTestId('cluster-member-create-email').fill(`${surname.toLowerCase()}@example.test`)
        await page.getByTestId('cluster-member-create-save').click()

        await expect(page.getByTestId('cluster-member-create-modal')).toHaveCount(0, {timeout: 15000})
        await expect(page.getByTestId('member-row').filter({hasText: surname}).first())
            .toBeVisible({timeout: 15000})
        await page.context().close()
    })

    /**
     * CLS-69 - The association exports the people across its stations.
     *
     * The station's own column picker and export modal, mounted whole and guarded by the association's
     * export right. It was written down as decided and never walked, which left an open question about
     * whether the button did anything: it hands over a file built from the rows on screen, so there is
     * no endpoint behind it to be missing.
     */
    test('the association exports the members it can see', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_EXPORT')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members')
        await expect(page.getByTestId('member-row').first()).toBeVisible({timeout: 15000})

        await page.getByTestId('members-export').click()
        await page.getByTestId('member-select-all').click()
        await page.getByTestId('members-export-continue').click()

        const download = page.waitForEvent('download')
        await page.getByTestId('members-export-download').click()
        const file = await download

        expect(file.suggestedFilename()).toBe('verbandsmitglieder.csv')
        await page.context().close()
    })

    /**
     * CLS-71 - The association reads and adds to what is filed about one person.
     *
     * The member screen showed the questions and nothing else, so a document filed at the station was
     * invisible from the association. Reading and adding is the whole of it: the document belongs to
     * the station that holds the person, so labelling and removing stay there. The panel is the
     * station's own, so the document opens the same way and names who filed it.
     */
    test('the association files a document about somebody at one of its stations', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members')
        const row = page.getByTestId('member-row').first()
        await expect(row).toBeVisible({timeout: 15000})
        await row.getByRole('button').first().click()
        await expect(page).toHaveURL(/\/cluster\/members\/\d+$/)

        const panel = page.getByTestId('cluster-member-documents')
        await expect(panel).toBeVisible({timeout: 15000})

        await panel.getByRole('button', {name: 'Hochladen'}).click()
        const upload = page.getByRole('dialog')
        await upload.locator('input[type="file"]').setInputFiles({
            name: 'nachweis.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from('Ein Nachweis, vom Verband abgelegt.'),
        })

        const title = `Nachweis ${Date.now()}`
        await upload.getByPlaceholder('Wie das Dokument heißen soll').fill(title)
        await upload.getByRole('button', {name: 'Hochladen'}).click()

        const filed = panel.getByTestId('document-tile').filter({hasText: title})
        await expect(filed, 'the document comes back from the server').toBeVisible({timeout: 15000})

        await filed.click()
        const opened = page.getByRole('dialog')
        await expect(opened.getByText('Ein Nachweis, vom Verband abgelegt.')).toBeVisible()
        await expect(opened.getByTestId('document-uploader'), 'the manager is named as who filed it').toBeVisible()

        const download = page.waitForEvent('download')
        await opened.getByRole('button', {name: 'Herunterladen'}).click()
        expect((await download).suggestedFilename()).toBe('nachweis.txt')

        await page.context().close()
    })
})
