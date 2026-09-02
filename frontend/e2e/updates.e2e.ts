/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'

/**
 * Whether a newer release exists is read from GitHub, which the suite cannot reach and would not
 * want to depend on: the answer would change with every release and the stories would go red on a
 * network hiccup rather than on a defect. What the suite can hold is everything around that answer,
 * and the part that matters is who is allowed to ask.
 */
test.describe('Update check', () => {
    /**
     * What version an instance runs is exactly what somebody hunting unpatched instances is looking
     * for, so it is not answered to ordinary members. The story asks as one and expects to be
     * refused rather than told.
     */
    test('an ordinary member is not told which version the instance runs', async ({memberPage: page}) => {
        const response = await page.request.get('/api/v1/system/update', {headers: await apiHeaders(page)})

        expect(response.status(), `a member should be refused (${await response.text()})`).toBe(403)
    })

    /**
     * Whoever administers a station is told, and is told what this instance actually runs even where
     * the check has never reached GitHub. An instance with no way out reports no update rather than
     * failing, which is the ordinary state behind a firewall.
     */
    test('whoever administers a station is told what this instance runs', async ({managerPage: page}) => {
        const response = await page.request.get('/api/v1/system/update', {headers: await apiHeaders(page)})
        expect(response.status(), `a manager should be answered (${await response.text()})`).toBe(200)

        const status = await response.json()
        expect(status.currentVersion).toBeTruthy()
        expect(typeof status.updateAvailable).toBe('boolean')
    })
})
