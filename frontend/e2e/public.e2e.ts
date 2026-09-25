/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'

/**
 * What a stranger sees of a station. No session and no fixture: these pages are the reason the
 * public part of the application exists, and anyone at all may read them.
 *
 * The station is addressed by its readable slug, the way a link to it would be written.
 */
const STATION = '/public/station/jugendfeuerwehr-musterstadt'
/** The public page list is addressed the same way, by the readable name of the station. */
const STATION_UID = 'jugendfeuerwehr-musterstadt'

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

/**
 * A survey somebody was sent the link to. No session, no station in the address, and nothing around
 * the form but the name of the station asking: the shape of a form sent in a message.
 *
 * The link is asked for the way a manager asks for it, rather than written down here, because that
 * is the half of the feature that decides whether anybody can find it at all.
 */
async function sharedPollLink(page: Page): Promise<string> {
    const headers = await apiHeaders(page)
    const list = await page.request.get('/api/v1/forms?purpose=POLL', {headers})
    const polls = await list.json()
    const poll = polls.find((p: {title: string}) => p.title.includes('Aktivität')) ?? polls[0]

    const existing = await page.request.get(`/api/v1/forms/${poll.id}/share-link`, {headers})
    const token = (await existing.json()).token
    if (token) return `/f/${token}`

    const made = await page.request.post(`/api/v1/forms/${poll.id}/share-link`, {
        headers,
        data: {currentToken: null},
    })
    return `/f/${(await made.json()).token}`
}

test.describe.serial('A survey sent by link', () => {
    test('a manager takes the link and a stranger answers the survey with it', async ({managerPage, page}) => {
        const path = await sharedPollLink(managerPage)
        await page.goto(path)

        await expect(page.getByText('Was sollen wir als nächstes machen?')).toBeVisible()
        await page.getByText('Zeltlager').click()
        await page.getByRole('checkbox').first().check()
        await page.getByRole('button', {name: 'Absenden'}).click()

        await expect(page.getByText('Danke für deine Antwort!')).toBeVisible()
    })

    test('the page names the station and offers nothing to browse into', async ({managerPage, page}) => {
        const path = await sharedPollLink(managerPage)
        await page.goto(path)

        await expect(page.getByText('Jugendfeuerwehr Musterstadt').first()).toBeVisible()
        await expect(page.getByRole('link', {name: 'Kalender'})).toHaveCount(0)
        await expect(page.getByRole('link', {name: 'Wiki'})).toHaveCount(0)
    })

    test('a link nobody minted opens nothing', async ({page}) => {
        await page.goto('/f/es-gibt-keinen-solchen-link')

        await expect(page.getByRole('button', {name: 'Absenden'})).toHaveCount(0)
    })
})

/**
 * A page reachable by its link alone. The same shape as the survey above, one layer out: the link
 * opens one page, and nothing about the station's own site is reachable from it.
 */
async function unlistedPage(page: Page): Promise<{id: number; slug: string}> {
    const headers = await apiHeaders(page)
    const list = await page.request.get('/api/v1/pages', {headers})
    const {pages} = await list.json()
    return pages.find((p: {visibility: string}) => p.visibility === 'UNLISTED')
}

async function sharedPageLink(page: Page): Promise<string> {
    const headers = await apiHeaders(page)
    const unlisted = await unlistedPage(page)
    const link = await page.request.get(`/api/v1/pages/${unlisted.id}/share-link`, {headers})
    return `/s/${(await link.json()).token}`
}

test.describe('A page reached by its link', () => {
    test('a stranger opens the link and reads the page', async ({managerPage, page}) => {
        const path = await sharedPageLink(managerPage)
        await page.goto(path)

        await expect(page.getByText('Einladung zum Sommerfest').first()).toBeVisible()
        await expect(page.getByRole('link', {name: 'Kalender'})).toHaveCount(0)
    })

    test('the same page is in no menu, in no list and at no address of its own', async ({managerPage, page}) => {
        const unlisted = await unlistedPage(managerPage)

        const listed = await page.request.get(`/api/v1/public/pages/${STATION_UID}`)
        const summaries = await listed.json()
        expect(summaries.some((p: {id: number}) => p.id === unlisted.id)).toBe(false)

        await page.goto(`${STATION}/page/${unlisted.slug}`)
        await expect(page.getByText('Einladung zum Sommerfest')).toHaveCount(0)
    })
})
