/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {apiHeaders, expect, test} from './fixtures/auth'
import {homePublishedUrl} from './fixtures/peer'
import {CSRF_HEADER} from './fixtures/session'

/**
 * The session is a cookie no script can read, and a change made with it has to prove it came from a
 * page of this application.
 *
 * <p>Both halves are what the browser does with the cookie rather than what a screen shows, so the
 * stories ask the page and the server directly. The change they try is drawing the member table,
 * which is a POST that alters nothing and is therefore safe to have go through if the refusal were
 * ever missing.
 */
test.describe('The session cookie', () => {
    const CHANGE = '/api/v1/member-table'

    test('a script on the page sees no session to steal', async ({managerPage: page}) => {
        await page.goto('/station/dashboard/overview')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        const readable = await page.evaluate(() => document.cookie)
        expect(readable, 'the session cookie is out of reach of scripts').not.toContain('ember_session')
        expect(readable, 'the token a change sends back is readable').toContain('ember_csrf')

        const stored = await page.evaluate(() => Object.keys(window.localStorage))
        expect(stored, 'nothing token-shaped is kept in local storage').not.toContain('session_token')
    })

    test('a change without the page token is refused and one with it is not', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const withoutToken = Object.fromEntries(Object.entries(headers).filter(([name]) => name !== CSRF_HEADER))

        const refused = await page.request.post(CHANGE, {headers: withoutToken, data: {}})
        expect(refused.status()).toBe(403)
        expect((await refused.json()).code).toBe('M-121')

        const forged = await page.request.post(CHANGE, {headers: {...headers, [CSRF_HEADER]: 'forged'}, data: {}})
        expect(forged.status()).toBe(403)

        const accepted = await page.request.post(CHANGE, {headers, data: {}})
        expect(accepted.status(), 'the page token lets the change through').not.toBe(403)
    })

    test('a form another site posts arrives without the session', async ({managerPage: page}) => {
        const target = new URL(CHANGE, homePublishedUrl()).toString()
        await page.route('https://another-site.example/', route => route.fulfill({
            contentType: 'text/html',
            body: `<form method="post" action="${target}"><button>Los</button></form>`,
        }))
        await page.goto('https://another-site.example/')

        const answered = page.waitForResponse(response => response.url() === target)
        await page.getByRole('button', {name: 'Los'}).click()

        expect([401, 403], 'the server never sees a session it would act on').toContain((await answered).status())

        await page.goto('/station/dashboard/overview')
        await expect(page.getByTestId('app-shell'), 'the session itself is untouched').toBeVisible()
    })
})
