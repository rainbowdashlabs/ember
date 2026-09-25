/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test, type Page} from '@playwright/test'
import {accountWith} from './fixtures/auth'

/**
 * The public routes, run by the `ssr-no-js` project with JavaScript switched off.
 *
 * Without that, every one of these would pass on hydration alone and say nothing about whether the
 * route rules still server-render. What breaks here is a browser API touched during setup in a
 * component a public page happens to use - invisible in the browser, invisible in a component test,
 * and the first thing an anonymous visitor meets.
 *
 * Every story checks the status code before it looks at the page. A server error renders a page
 * with a heading on it too, so an assertion that only asks for a heading passes against a broken
 * server - which is exactly what it is there to catch.
 */
async function visit(page: Page, path: string) {
    const response = await page.goto(path)
    expect(response?.status(), `${path} answered ${response?.status()}`).toBeLessThan(400)
}

test.describe('Public pages without JavaScript', () => {
    test('the landing page is server-rendered', async ({page}) => {
        await visit(page, '/')
        await expect(page.getByRole('heading').first()).toBeVisible()
        await expect(page.getByRole('link', {name: /Login|Anmelden/i}).first()).toBeVisible()
    })

    /**
     * The form itself is deliberately not part of this. It appears once the storage consent has
     * been resolved, and resolving it is client-side work - so with JavaScript off the page shows
     * the consent gate, which is the correct behaviour rather than a defect. What the story holds
     * the route rule to is that the page arrives from the server at all.
     */
    test('the login page is server-rendered', async ({page}) => {
        await visit(page, '/login')
        await expect(page.getByRole('heading').first()).toBeVisible()
    })

    /**
     * The entries, not a heading: this page titles itself in a plain element, and what a crawler
     * has to find here is the stations themselves - each one linking to its public page.
     */
    test('the station directory is server-rendered', async ({page}) => {
        await visit(page, '/discovery')
        await expect(page.locator('a[href^="/public/station/"]').first()).toBeVisible()
    })

    /**
     * The text itself, not just a heading: these pages exist to carry a document, and an instance
     * lays one down in each language on its first start. A page that arrives with its frame and no
     * document would pass a weaker assertion while telling a visitor nothing.
     */
    /**
     * Every public page of a station hangs inside one shell, and the shell used to load itself in
     * the browser - so a crawler and a link preview met an empty frame where the station's blog and
     * wiki should be.
     */
    test('a public station carries its name and its pages', async ({page}) => {
        for (const path of ['', '/blog', '/knowledge']) {
            await visit(page, `/public/station/jugendfeuerwehr-musterstadt${path}`)
            await expect(page.getByText('Jugendfeuerwehr Musterstadt').first()).toBeVisible()
        }
    })

    test('the legal pages are server-rendered', async ({page}) => {
        const legal: [string, string][] = [
            ['/imprint', 'Impressum'],
            ['/privacy', 'Datenschutzerklärung'],
            ['/terms', 'Nutzungsbedingungen'],
        ]
        for (const [path, heading] of legal) {
            await visit(page, path)
            await expect(page.getByRole('heading', {name: heading}).first()).toBeVisible()
        }
    })

    /**
     * A link whose whole purpose is to be pasted into a message. The questions have to be in the
     * page the server sends, or the preview drawn beside the link says nothing, and the instruction
     * not to index it has to be there too, since a crawler that runs no scripts would otherwise
     * never see one.
     *
     * The link is asked for over plain HTTP rather than through the application, because this
     * project runs with scripts switched off and cannot press a button to get one.
     */
    test('a survey sent by link carries its questions and refuses to be indexed', async ({page, request}) => {
        const manager = await accountWith(request, 'POLL_CREATE')
        const session = await (await request.post('/api/v1/demo/login', {data: {email: manager.email}})).json()
        const headers = {Authorization: `Bearer ${session.token}`, 'X-Station-Id': String(manager.stationId)}

        const polls = await (await request.get('/api/v1/forms?purpose=POLL', {headers})).json()
        const link = await (await request.get(`/api/v1/forms/${polls[0].id}/share-link`, {headers})).json()

        await visit(page, `/f/${link.token}`)

        await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', /noindex/)
        await expect(page.getByRole('button', {name: 'Absenden'})).toBeVisible()
    })
})
