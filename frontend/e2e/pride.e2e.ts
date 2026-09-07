/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, freshStepUpProof} from './fixtures/auth'

/**
 * The rainbow flag an instance can switch on all year round.
 *
 * <p>It is worth a story of its own because it broke in a way nothing else would catch: whether it is
 * forced on is known only once the browser has asked, so a server-rendered page always renders it off,
 * and Vue keeps whatever the server said for any class that disagrees at hydration. The flag was
 * therefore correct inside the application, which is rendered in the browser, and dead on every page
 * that is rendered by the server: the landing page, the login page, the public station pages.
 */
test.describe('Pride flag', () => {
    test('an instance that forces the flag shows it on a server-rendered page too',
        async ({adminPage}) => {
            const headers = await apiHeaders(adminPage)
            // The settings are written straight over the API, where no dialog can ask: the fresh
            // proof every session owes on the guarded routes is given up front.
            await freshStepUpProof(adminPage)
            const before = await adminPage.request.get('/api/v1/admin/settings', {headers})
            const settings = await before.json()

            const force = async (forcePrideFlag: boolean) => {
                const saved = await adminPage.request.put('/api/v1/admin/settings',
                    {headers, data: {...settings, forcePrideFlag}})
                expect(saved.ok(), `the instance set the flag to ${forcePrideFlag}`).toBeTruthy()
            }

            try {
                await force(true)

                const visitor = await adminPage.context().browser()!.newContext()
                const landing = await visitor.newPage()
                await landing.goto('/')
                await expect(landing.locator('.pride-flag').first()).toBeVisible({timeout: 15000})

                await landing.goto('/login')
                await expect(landing.locator('.pride-flag').first()).toBeVisible({timeout: 15000})
                await visitor.close()
            } finally {
                await force(settings.forcePrideFlag === true)
            }
        })
})
