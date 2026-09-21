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

/**
 * What the instance changed, read from the instance.
 *
 * <p>This is the half that does not depend on GitHub at all, which is the point of it: the notes
 * travel in the jar, so they are here to be asked for whether or not anything outside can be
 * reached.
 */
test.describe('Changelog', () => {
    /** Public, like the version the footer already shows beside the link to this page. */
    test('the changelog is served by the instance to anybody who asks', async ({page}) => {
        const response = await page.request.get('/api/v1/public/changelog')
        expect(response.ok(), await response.text()).toBe(true)

        const entries = await response.json()
        expect(entries.length, 'the changelog travels with the instance').toBeGreaterThan(0)
        expect(entries[0].version).toMatch(/^\d+(\.\d+)*$/)
        expect(entries[0].body.length).toBeGreaterThan(0)
    })

    /** German is what the product speaks, and the newest version is the one that has to be in it. */
    test('the newest version reads German', async ({page}) => {
        const response = await page.request.get('/api/v1/public/changelog?lang=de')
        const [newest] = await response.json()

        expect(newest.body).toMatch(/Neue Funktionen|Änderungen|Fehlerbehebungen|Verbesserungen/)
    })

    /**
     * The page draws what the instance served and asks nobody else for it. A story that only looked
     * at the text would pass just as well against the GitHub call this replaced.
     */
    test('the page draws the changelog without calling out to GitHub', async ({page}) => {
        const outward: string[] = []
        await page.route('https://api.github.com/**', async route => {
            outward.push(route.request().url())
            await route.abort()
        })

        await page.goto('/patch-notes')
        const versions = page.getByTestId('changelog-version')
        await expect(versions.first()).toBeVisible()

        expect(outward, 'nothing is asked of GitHub').toEqual([])
    })

    /**
     * The running version is marked where the changelog lists it, and nowhere where it does not.
     *
     * <p>The version moves on right after a release, so between releases an instance runs a version
     * the changelog has not reached yet. The story asks the instance which one it runs rather than
     * assuming it was released.
     */
    test('the running version is marked when it has been released', async ({page}) => {
        const config = await (await page.request.get('/api/v1/public/config')).json()
        const running = String(config.version ?? '').trim().split(' ')[0]!.replace(/^v/, '')
        const changelog = await (await page.request.get('/api/v1/public/changelog')).json()
        const released = changelog.some((entry: {version: string}) => entry.version === running)

        await page.goto('/patch-notes')
        await expect(page.getByTestId('changelog-version').first()).toBeVisible()

        const badge = page.getByTestId('changelog-current')
        if (released) await expect(badge).toBeVisible()
        else await expect(badge).toHaveCount(0)
    })
})
