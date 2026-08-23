/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test, type Page} from '@playwright/test'

/**
 * The deck exists only where the instance invites people to look around. It asks the instance what
 * it is before it draws anything, so a story that wants a kind other than the one the suite runs
 * against answers that question itself rather than needing a second stack.
 */
async function instanceIs(page: Page, status: {demo: boolean; dev: boolean}) {
    await page.route('**/api/v1/demo/status', route => route.fulfill({json: status}))
}

test.describe('Pitch deck', () => {
    test('a demo instance opens the deck', async ({page}) => {
        await instanceIs(page, {demo: true, dev: false})

        await page.goto('/pitch')

        await expect(page.getByText('Eure Gruppe.')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Vollbild'})).toBeVisible()
    })

    test('a development instance opens the deck', async ({page}) => {
        await instanceIs(page, {demo: false, dev: true})

        await page.goto('/pitch')

        await expect(page.getByText('Eure Gruppe.')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Vollbild'})).toBeVisible()
    })

    test('the instance the suite runs against opens the deck', async ({page}) => {
        await page.goto('/pitch')

        await expect(page.getByText('Eure Gruppe.')).toBeVisible()
    })

    test('a position in the address opens that slide', async ({page}) => {
        await instanceIs(page, {demo: true, dev: false})

        await page.goto('/pitch/2/1')

        await expect(page.getByText(/^2 \/ \d+$/)).toBeVisible()
        await expect(page.getByText('Eure Gruppe.')).toHaveCount(0)
    })

    test('any other instance answers as if the address were not there', async ({page}) => {
        await instanceIs(page, {demo: false, dev: false})

        await page.goto('/pitch')

        await expect(page.getByText('404')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Vollbild'})).toHaveCount(0)
    })
})
