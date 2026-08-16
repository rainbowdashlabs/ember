/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test, type Page} from '@playwright/test'

/**
 * The public routes, run by the `ssr-no-js` project with JavaScript switched off.
 *
 * Without that, every one of these would pass on hydration alone and say nothing about whether the
 * route rules still server-render. What breaks here is a browser API touched during setup in a
 * component a public page happens to use — invisible in the browser, invisible in a component test,
 * and the first thing an anonymous visitor meets.
 *
 * Every story checks the status code before it looks at the page. A server error renders a page
 * with a heading on it too, so an assertion that only asks for a heading passes against a broken
 * server — which is exactly what it is there to catch.
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
     * been resolved, and resolving it is client-side work — so with JavaScript off the page shows
     * the consent gate, which is the correct behaviour rather than a defect. What the story holds
     * the route rule to is that the page arrives from the server at all.
     */
    test('the login page is server-rendered', async ({page}) => {
        await visit(page, '/login')
        await expect(page.getByRole('heading').first()).toBeVisible()
    })

    /**
     * The entries, not a heading: this page titles itself in a plain element, and what a crawler
     * has to find here is the stations themselves — each one linking to its public page.
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
    test('the legal pages are server-rendered', async ({page}) => {
        for (const [path, heading] of [
            ['/imprint', 'Impressum'],
            ['/privacy', 'Datenschutzerklärung'],
            ['/terms', 'Nutzungsbedingungen'],
        ]) {
            await visit(page, path)
            await expect(page.getByRole('heading', {name: heading}).first()).toBeVisible()
        }
    })
})
