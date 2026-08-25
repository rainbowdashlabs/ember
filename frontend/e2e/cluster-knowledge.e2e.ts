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
})
