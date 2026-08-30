/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import type {Page} from '@playwright/test'

/**
 * A member who is short of something they are supposed to hold, found the way the page finds them.
 *
 * The seeded station decides who that is, so the story asks rather than naming somebody: a fixed
 * name would break the first time the seeder hands that person a jacket.
 */
async function memberWhoIsShort(page: Page): Promise<number | null> {
    // The session lives in local storage, not in a cookie, so a bare request from the page's
    // context carries no authorisation at all and every call comes back refused.
    const headers = await page.evaluate(() => ({
        Authorization: `Bearer ${window.localStorage.getItem('session_token') ?? ''}`,
        'X-Station-Id': window.localStorage.getItem('station_id') ?? '',
    }))

    const overview = await page.request.get('/api/v1/inventory-checks', {headers})
    expect(overview.ok(), 'the stock-taking overview answers').toBeTruthy()
    for (const summary of await overview.json()) {
        const answer = await page.request.get(
            `/api/v1/station-members/${summary.memberId}/inventory-requirements`, {headers})
        if (!answer.ok()) continue
        const {required} = await answer.json()
        if (required.some((req: {assignedQuantity: number; requiredQuantity: number}) =>
            req.assignedQuantity < req.requiredQuantity)) {
            return summary.memberId
        }
    }
    return null
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

        const memberId = await memberWhoIsShort(page)
        expect(memberId, 'the seeded station has somebody short of something').not.toBeNull()

        await page.goto(`/station/members/detail/${memberId}`)
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
    })
})
