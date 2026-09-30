/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test, type Page} from '@playwright/test'

/**
 * The public pages the server renders, taken up by the browser without a word from Vue.
 *
 * <p>A page whose first render in the browser differs from the markup the server sent is repaired
 * by Vue where the difference is in its structure and kept as it is where it is only an attribute,
 * so a class the server chose for a desk stays on a phone until something redraws it. Both are
 * reported in the console as a hydration mismatch, and that report is what fails a story here.
 * Every page is also opened at the width of a phone, which is where the differences that come from
 * the size of the screen show, in every project rather than only in `mobile`.
 */
const PAGES: [string, string][] = [
    ['the landing page', '/'],
    ['the login page', '/login'],
    ['the station directory', '/discovery'],
    ['a public station', '/public/station/jugendfeuerwehr-musterstadt'],
    ['a help page', '/helpcenter/station/basics'],
    ['the pitch deck', '/pitch'],
]

/** Collects what the browser reports about hydration while the page is taken up. */
function hydrationReports(page: Page): string[] {
    const reports: string[] = []
    page.on('console', message => {
        if (message.type() === 'error' && /hydration/i.test(message.text())) reports.push(message.text())
    })
    return reports
}

/** Waits until Nuxt has finished taking up the page the server sent. */
async function hydrated(page: Page): Promise<void> {
    await page.waitForFunction(() => {
        const nuxt = (window as unknown as {useNuxtApp?: () => {isHydrating: boolean}}).useNuxtApp
        return nuxt !== undefined && !nuxt().isHydrating
    })
}

/** The width of a phone, so every project reaches what a narrow screen renders differently. */
const PHONE = {width: 390, height: 844}

/** One story per page, at whatever size the describe block around it sets. */
function everyPageMatches() {
    for (const [name, path] of PAGES) {
        test(`${name} matches what the server sent`, async ({page}) => {
            const reports = hydrationReports(page)

            const response = await page.goto(path)
            expect(response?.status(), `${path} answered ${response?.status()}`).toBeLessThan(400)
            await hydrated(page)

            expect(reports).toEqual([])
        })
    }
}

test.describe('Public pages in the browser', () => {
    everyPageMatches()
})

test.describe('Public pages in the browser of a phone', () => {
    test.use({viewport: PHONE})
    everyPageMatches()
})
