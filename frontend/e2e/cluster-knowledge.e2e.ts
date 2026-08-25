/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {ownCluster} from './fixtures/cluster'

/**
 * The association's wiki, and who may see what is in it.
 *
 * Every story builds an association of its own. Putting a wiki on the public web is a setting of the whole
 * association, so doing it to the seeded one would publish a wiki three other workers are reading.
 */
test.describe('Cluster knowledge', () => {
    /**
     * CLS-107 - An association puts its wiki on the public web and an article is readable there.
     *
     * The association could not do this at all: the switch is the station's, the wiki is the station's, and
     * an association cannot reach a station's settings. So the field that marks one article public was drawn
     * only where the switch was on, and the switch could never be turned on. The story turns it on from the
     * association's own wiki page and then reads the result with no session at all, which is the only way to
     * know the switch means what it says.
     */
    test('an association turns its public wiki on and an article is readable there',
        async ({adminPage: page, browser, request}) => {
            const own = await ownCluster(page, browser, request, 'Wikiverband')
            const homeStationUid = own.contentHeaders['X-Station-Id']!
            const name = `Dienstanweisung ${test.info().workerIndex}-${Date.now()}`

            const written = await page.request.post('/api/v1/kb/files/markdown', {
                headers: own.contentHeaders,
                data: {folderId: null, name, description: 'Gilt für alle Wachen', content: '# Gilt für alle'},
            })
            expect(written.ok(), `the association wrote an article (${await written.text()})`).toBeTruthy()

            await page.goto('/cluster/knowledge')
            await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), own.uid)
            await page.goto('/cluster/knowledge')

            const toggle = page.getByRole('switch')
            await expect(toggle).toBeVisible({timeout: 15000})
            await toggle.click()

            const stranger = await browser.newContext()
            const publicPage = await stranger.newPage()

            // The switch is saved as it is flipped, so the public wiki answers without anything else pressed
            await expect(async () => {
                await publicPage.goto(`/public/station/${homeStationUid}/knowledge`)
                await expect(publicPage.getByText(name).first()).toBeVisible({timeout: 5000})
            }).toPass({timeout: 30000})

            await stranger.close()
            await own.stationPage.context().close()
        })

    /**
     * CLS-110 - A folder the association writes arrives at its stations as a folder.
     *
     * It did not arrive at all. The association's wiki reached its stations as a flat list of articles:
     * the folder was shared, the share row written, and the browse dropped every folder share on its first
     * line because it had no shape to return one in. So an association wrote a structure and its stations
     * received a heap. The story asserts the folder arrives badged with the association and its article
     * does not stand loose beside it, then opens the folder and finds the article inside.
     */
    test('a folder the association writes arrives at its stations as a folder',
        async ({adminPage: page, browser, request}) => {
            const own = await ownCluster(page, browser, request, 'Ordnerverband')
            const stamp = `${test.info().workerIndex}-${Date.now()}`
            const folderName = `Dienstanweisungen ${stamp}`
            const fileName = `Einsatzkleidung ${stamp}`

            const folder = await page.request.post('/api/v1/kb/folders', {
                headers: own.contentHeaders,
                data: {parentId: null, name: folderName, description: 'Gilt für alle Wachen'},
            })
            expect(folder.ok(), `the association made a folder (${await folder.text()})`).toBeTruthy()
            const folderId = (await folder.json()).id

            const written = await page.request.post('/api/v1/kb/files/markdown', {
                headers: own.contentHeaders,
                data: {folderId, name: fileName, description: 'Pflege und Tausch', content: '# Pflege'},
            })
            expect(written.ok(), `and an article inside it (${await written.text()})`).toBeTruthy()

            await own.stationPage.goto('/station/knowledge')
            await expect(own.stationPage.getByTestId('app-shell')).toBeVisible()

            const arrived = own.stationPage.getByTestId('kb-item').filter({hasText: folderName})
            await expect(arrived).toHaveCount(1, {timeout: 15000})
            await expect(arrived).toContainText(own.name)
            await expect(own.stationPage.getByTestId('kb-item').filter({hasText: fileName})).toHaveCount(0)

            await arrived.click()
            await expect(own.stationPage.getByTestId('kb-item').filter({hasText: fileName}))
                .toHaveCount(1, {timeout: 15000})

            await own.stationPage.context().close()
        })

    /**
     * CLS-108 - An article for named stations reaches those and not the rest.
     *
     * The association's wiki went to every station under it, always: the share was written for everybody
     * the moment anything was created and nothing could say otherwise. The scope and the table to hold the
     * names both existed; the route carried no list and no screen offered one.
     */
    test('an article for named stations reaches those and not the rest',
        async ({adminPage: page, browser, request}) => {
            const own = await ownCluster(page, browser, request, 'Zielverband')
            const outside = await page.request.post('/api/v1/cluster/stations',
                {headers: own.headers, data: {name: `${own.name} Aussen`}})
            expect(outside.ok(), `the association made a second station (${await outside.text()})`).toBeTruthy()

            const name = `Nur für eine Wache ${test.info().workerIndex}-${Date.now()}`
            const written = await page.request.post('/api/v1/kb/files/markdown', {
                headers: own.contentHeaders,
                data: {folderId: null, name, description: 'Zielgerichtet', content: '# Nur hier'},
            })
            expect(written.ok(), `the association wrote an article (${await written.text()})`).toBeTruthy()
            const fileId = (await written.json()).id

            // The station the story can read as is the one it keeps a page for, so that is the one named
            const partners = await page.request
                .get('/api/v1/federation/partners', {headers: own.contentHeaders})
                .then(r => r.json())
            const named = partners.find((held: {partnerStationName: string}) =>
                held.partnerStationName === own.stationName)
            expect(named, 'the association keeps a partnership with its own station').toBeTruthy()

            const aimed = await page.request.put('/api/v1/cluster/knowledge/audiences', {
                headers: own.headers,
                data: {fileId, everyStation: false, partnerIds: [named.partner.id]},
            })
            expect(aimed.ok(), `and aimed the article at it (${await aimed.text()})`).toBeTruthy()

            await own.stationPage.goto('/station/knowledge')
            await expect(own.stationPage.getByTestId('kb-item').filter({hasText: name}))
                .toHaveCount(1, {timeout: 15000})

            const away = await page.request.put('/api/v1/cluster/knowledge/audiences', {
                headers: own.headers,
                data: {fileId, everyStation: false, partnerIds: []},
            })
            expect(away.ok(), `and then at nobody (${await away.text()})`).toBeTruthy()

            await own.stationPage.reload()
            await expect(own.stationPage.getByTestId('kb-item').filter({hasText: name}))
                .toHaveCount(0, {timeout: 15000})

            await own.stationPage.context().close()
        })
})
