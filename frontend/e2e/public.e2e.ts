/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from '@playwright/test'

/**
 * What a stranger sees of a station. No session and no fixture: these pages are the reason the
 * public part of the application exists, and anyone at all may read them.
 *
 * The station is addressed by its readable slug, the way a link to it would be written.
 */
const STATION = '/public/station/jugendfeuerwehr-musterstadt'

test.describe('A station seen from outside', () => {
    test('the landing page carries the station and its menu', async ({page}) => {
        await page.goto(STATION)

        await expect(page.getByText('Jugendfeuerwehr Musterstadt').first()).toBeVisible()
    })

    test('the blog lists articles and an article opens', async ({page}) => {
        await page.goto(`${STATION}/blog`)

        const article = page.locator('main a[href*="/blog/"], main [class*="cursor-pointer"]').first()
        await expect(article).toBeVisible()

        await article.click()
        await expect(page.getByRole('heading').first()).toBeVisible()
    })

    test('a public page of the station opens by its address', async ({page}) => {
        await page.goto(`${STATION}/page/willkommen`)

        await expect(page.getByText('Jugendfeuerwehr Musterstadt').first()).toBeVisible()
    })

    test('the calendar of the station is public', async ({page}) => {
        await page.goto(`${STATION}/calendar`)

        await expect(page.getByText('Jugendfeuerwehr Musterstadt').first()).toBeVisible()
    })

    test('the waiting list takes a registration', async ({page}) => {
        await page.goto(`${STATION}/waitlist`)

        await expect(page.getByText('Jugendfeuerwehr Musterstadt').first()).toBeVisible()
    })
})
