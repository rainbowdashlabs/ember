/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import type {Page} from '@playwright/test'
import {unique} from './fixtures/unique'

/**
 * What a request needs to act as the manager the story is signed in as.
 *
 * The session lives in local storage, not in a cookie, so a bare request from the page's context
 * carries no authorisation at all and every call comes back refused.
 */
async function managerHeaders(page: Page): Promise<Record<string, string>> {
    return page.evaluate(() => ({
        Authorization: `Bearer ${window.localStorage.getItem('session_token') ?? ''}`,
        'X-Station-Id': window.localStorage.getItem('station_id') ?? '',
    }))
}

/** A member the station keeps gear for, with the type a requirement can be written against. */
async function someoneHoldingGear(page: Page, headers: Record<string, string>) {
    const overview = await page.request.get('/api/v1/inventory-checks', {headers})
    expect(overview.ok(), 'the stock-taking overview answers').toBeTruthy()

    const summaries = await overview.json()
    expect(summaries.length, 'the seeded station keeps gear for somebody').toBeGreaterThan(0)
    return summaries[0] as {memberId: number, userType: string}
}

/**
 * A shortage of this story's own making: a piece of gear nobody holds, required of a member's type.
 *
 * <p>The story used to search the seeded station for whoever happened to be short of something, and
 * handing that piece over is exactly what takes the shortage away. So the first story to reach it
 * resolved it for every story after, and this one failed whenever it went second, on a retry or
 * behind a neighbour in the same batch. Bringing its own gear, which nothing else knows about,
 * leaves nothing to race over.
 */
async function shortageOfItsOwn(page: Page, headers: Record<string, string>, userType: string) {
    const gear = await page.request.post('/api/v1/inventories', {
        headers,
        data: {name: unique('Prüfstück'), inventoryType: 'INTERNAL', hasSizes: false},
    })
    expect(gear.ok(), 'the piece of gear is created').toBeTruthy()
    const inventoryId = (await gear.json()).id as number

    const requirement = await page.request.post('/api/v1/inventory-requirements', {
        headers,
        data: {inventoryId, userType, quantity: 1},
    })
    expect(requirement.ok(), 'the gear is required of the member').toBeTruthy()

    return {inventoryId, requirementId: (await requirement.json()).id as number}
}

test.describe('Member equipment requirements', () => {
    /**
     * INV-40 - What a member is owed stands on their own page.
     *
     * The tab used to list only what had been handed over, so a gap was invisible until somebody
     * started a stock-taking. The story opens a member who is short and hands the missing piece over
     * from there, which is the whole point of showing it.
     */
    test('a missing piece is handed over from the member page', async ({managerPage: page}) => {
        await page.goto('/station/members/list')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        const headers = await managerHeaders(page)
        const member = await someoneHoldingGear(page, headers)
        const {inventoryId, requirementId} = await shortageOfItsOwn(page, headers, member.userType)

        try {
            await page.goto(`/station/members/detail/${member.memberId}`)
            await page.getByRole('button', {name: 'Inventar'}).click()

            const card = page.getByTestId('missing-requirement').first()
            await expect(card).toBeVisible()

            const before = page.getByTestId('inventory-item-card')
            const held = await before.count()

            // Where the store keeps sizes the piece cannot be written down without one, and the button
            // stays disabled until it is chosen.
            const size = card.locator('select').filter({has: page.locator('option:text-is("Größe wählen")')})
            if (await size.count() > 0) await size.selectOption({index: 1})

            await card.getByRole('button', {name: 'Neu anlegen und zuweisen'}).click()

            await expect(page.getByTestId('inventory-item-card')).toHaveCount(held + 1, {timeout: 15000})
        } finally {
            await page.request.delete(`/api/v1/inventory-requirements/${requirementId}`, {headers})
            await page.request.delete(`/api/v1/inventories/${inventoryId}`, {headers})
        }
    })
})
