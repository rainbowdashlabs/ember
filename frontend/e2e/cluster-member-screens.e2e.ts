/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, clusterAccountWith, clusterHeaders, clusterPage, theSeededCluster} from './fixtures/auth'
import {ownCluster} from './fixtures/cluster'

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

        // The table's own rows, not a hand rolled stack.
        await expect(page.getByTestId('member-row').first()).toBeVisible({timeout: 15000})

        // Narrowing to one station is offered, because more than one is in view.
        await expect(page.locator('select').first()).toBeVisible()
        await page.context().close()
    })

    /**
     * CLS-96 - The people at the stations are shown as people.
     *
     * CLS-63 asserted the rows existed, and a row with an empty name is still a row. The search
     * handed the browser a name nothing reads and a null identity, which is what every list draws a
     * person from, so every line carried a blank space beside a blank avatar.
     */
    test('every row on the station member list carries a name', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)
        const cluster = await theSeededCluster(page)
        const headers = await clusterHeaders(page, cluster)

        const found = await page.request
            .get('/api/v1/cluster/members/manage/search?size=5', {headers})
            .then(r => r.json())
        const names: string[] = found.members.map((row: {name: string}) => row.name).filter(Boolean)
        expect(names.length, 'the association has people at its stations').toBeGreaterThan(0)
        expect(found.members.every((row: {identity: unknown}) => !!row.identity),
            'and the search says who each of them is').toBeTruthy()

        await page.goto('/cluster/members')
        await expect(page.getByTestId('member-row').first()).toBeVisible({timeout: 15000})

        const table = page.getByTestId('member-row')
        for (const name of names.slice(0, 3)) {
            await expect(table.filter({hasText: name}).first(),
                `${name} is drawn on the row rather than left blank`).toBeVisible({timeout: 15000})
        }

        await page.context().close()
    })

    /**
     * CLS-97 - The station is named only while it is worth naming, and then all of them are.
     *
     * The note was drawn unconditionally, so narrowing to one station left every row repeating the same
     * word. And the search returns one row per membership, so somebody at two stations of the
     * association was two rows that never said they were the same person.
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

            await page.goto('/cluster/members')
            await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), own.uid)
            await page.goto('/cluster/members')
            await expect(page.getByTestId('app-shell')).toBeVisible()

            const rows = page.getByTestId('member-row').filter({hasText: surname})
            await expect(rows).toHaveCount(2, {timeout: 15000})
            await expect(rows.first()).toContainText(own.stationName)
            await expect(rows.first(), 'and the row says both, not only the one it came from')
                .toContainText(`${own.name} Zweite`)

            await page.locator('select').first().selectOption({label: own.stationName})
            await expect(rows, 'one station in view leaves one of the two memberships')
                .toHaveCount(1, {timeout: 15000})
            await expect(rows.first(), 'and stops naming a station once there is only one')
                .not.toContainText(own.stationName)

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

        // Groups and tags belong to one station and are never a column here.
        await expect(page.getByRole('columnheader', {name: /Gruppen/i})).toHaveCount(0)
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

        // Adding is behind a button, so no email box is sitting on the page.
        await expect(page.getByPlaceholder(/@/)).toHaveCount(0)
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

        // The station first, because that is the question the association cannot answer for somebody
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
        // Every row on screen, which is what an association exports rather than one station's worth
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
     * the station that holds the person, so labelling and removing stay there.
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

        await page.getByTestId('cluster-member-document-upload').click()
        await page.getByTestId('cluster-member-document-file').locator('input[type=file]').setInputFiles({
            name: 'nachweis.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from('Ein Nachweis, vom Verband abgelegt.'),
        })

        const title = `Nachweis ${Date.now()}`
        await page.getByTestId('cluster-member-document-name').fill(title)
        await page.getByTestId('cluster-member-document-save').click()

        // It comes back from the server on the next read, not from what the form still holds
        const filed = panel.getByTestId('cluster-member-document').filter({hasText: title})
        await expect(filed).toBeVisible({timeout: 15000})

        // And the bytes come back too, which is the half of it the list cannot show
        const download = page.waitForEvent('download')
        await filed.getByTestId('cluster-member-document-download').click()
        expect((await download).suggestedFilename()).toBe('nachweis.txt')

        await page.context().close()
    })
})
