/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, answerStepUpPrompts, apiHeaders, stationOf} from './fixtures/auth'
import {sidebarEntry} from './fixtures/sidebar'
import {fillMoment, momentFields} from './fixtures/moment'
import {unique} from './fixtures/unique'

/** The stations the page's account belongs to, as the station switcher lists them. */
async function stationsOf(page: Page): Promise<string[]> {
    const answer = await page.request.get('/api/v1/session/stations', {headers: await apiHeaders(page)})
    expect(answer.ok()).toBeTruthy()
    return (await answer.json() as {stationId: string}[]).map(station => station.stationId)
}

/** Another tab of the same browser, which shares the sign-in and the stored choices with the first. */
async function anotherTab(page: Page): Promise<Page> {
    const tab = await page.context().newPage()
    await answerStepUpPrompts(tab)
    return tab
}

test.describe('Across stations', () => {
    /** This page stands outside a single station, so it carries its own frame rather than the
     * station shell every other story anchors on. */
    test('the combined view of every membership is reachable', async ({memberPage: page}) => {
        await page.goto('/cross-station')

        await expect(page.getByRole('heading', {name: 'Übersicht'})).toBeVisible()
        await expect(page.getByText('Alle Wachen auf einen Blick')).toBeVisible()
    })

    test('the station picker lists what a member belongs to', async ({memberPage: page}) => {
        await page.goto('/station-select')

        await expect(page.getByText(/Wache/).first()).toBeVisible()
    })
})

/**
 * Two stations side by side in two tabs of one browser.
 *
 * <p>The station chosen last used to be one value for the whole browser, and every request read it
 * anew. A tab still showing the first station then asked for and saved into the station another tab
 * had just moved to. Each tab now keeps its own, and every address names it.
 */
test.describe('A station per tab', () => {
    test('a tab keeps its station while another tab moves to a different one', async ({managerPage: first}) => {
        await first.goto('/station/members/list')
        await expect(first.getByTestId('app-shell')).toBeVisible()
        const own = await stationOf(first)
        const other = (await stationsOf(first)).find(station => station !== own)
        test.skip(!own || !other, 'the account needs two stations')

        await expect(first).toHaveURL(new RegExp(`station=${own}`))

        const second = await anotherTab(first)
        await second.goto(`/station/dashboard/overview?station=${other}`)
        await expect(second.getByTestId('app-shell')).toBeVisible()
        expect(await stationOf(second)).toBe(other)

        const asked = first.waitForRequest(request =>
            request.url().includes('/api/v1/station-members') && request.headers()['x-station-id'] !== undefined)
        await first.getByTestId('member-row').first().getByRole('link', {name: 'Details'}).click()

        expect((await asked).headers()['x-station-id'], 'the first tab still asks for its own station').toBe(own)
        await expect(first).toHaveURL(new RegExp(`/station/members/detail/\\d+\\?.*station=${own}`))
        expect(await stationOf(first)).toBe(own)
    })

    test('a link opened in a new tab opens the station it was found in', async ({managerPage: first}) => {
        await first.goto('/station/members/list')
        await expect(first.getByTestId('app-shell')).toBeVisible()
        const own = await stationOf(first)
        const other = (await stationsOf(first)).find(station => station !== own)
        test.skip(!own || !other, 'the account needs two stations')

        const second = await anotherTab(first)
        await second.goto(`/station/dashboard/overview?station=${other}`)
        await expect(second.getByTestId('app-shell')).toBeVisible()

        const href = await sidebarEntry(first, 'Mitgliederliste').first().getAttribute('href')
        expect(href, 'the link names the station of its tab').toContain(`station=${own}`)

        const third = await anotherTab(first)
        await third.goto(href!)
        await expect(third.getByTestId('app-shell')).toBeVisible()
        expect(await stationOf(third), 'the new tab opens where the link was found, not where the browser was last').toBe(own)
    })

    /** The worst of the old mix-up: an appointment written in one tab was saved in the other tab's station. */
    test('an appointment saved in a tab lands in the station of that tab', async ({managerPage: first}) => {
        await first.goto('/station/events/new')
        await expect(first.getByPlaceholder('Name des Termins')).toBeVisible()
        const own = await stationOf(first)
        const other = (await stationsOf(first)).find(station => station !== own)
        test.skip(!own || !other, 'the account needs two stations')

        const second = await anotherTab(first)
        await second.goto(`/station/dashboard/overview?station=${other}`)
        await expect(second.getByTestId('app-shell')).toBeVisible()

        const name = unique('Tab-Termin')
        await first.getByPlaceholder('Name des Termins').fill(name)
        const times = momentFields(first)
        await fillMoment(times.first(), '2026-12-10T18:00')
        if (await times.count() > 1) await fillMoment(times.nth(1), '2026-12-10T20:00')
        const saved = first.waitForResponse(response => response.request().method() === 'POST'
            && new URL(response.url()).pathname.startsWith('/api/v1/events'))
        await first.getByRole('button', {name: /Speichern|Erstellen/}).last().click()
        const answer = await saved
        expect(answer.ok(), 'the appointment was saved').toBeTruthy()
        expect(answer.request().headers()['x-station-id'], 'the appointment is sent for the station of its tab')
            .toBe(own)
        await first.waitForURL(url => !url.pathname.endsWith('/events/new'))

        await first.goto(`/station/events?search=${encodeURIComponent(name)}`)
        await first.getByTestId('event-entry').filter({hasText: name}).first().click()
        await first.waitForURL(/\/station\/events\/\d+/)
        const id = Number(/\/station\/events\/(\d+)/.exec(first.url())?.[1])

        const headers = await apiHeaders(first)
        expect((await first.request.get(`/api/v1/events/${id}`, {headers})).ok()).toBeTruthy()
        const elsewhere = await first.request.get(`/api/v1/events/${id}`, {headers: {...headers, 'X-Station-Id': other!}})
        expect(elsewhere.ok(), 'the other station does not hold it').toBeFalsy()
    })
})
