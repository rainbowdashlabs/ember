/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * A checklist without a single column has nothing to tick, and the dialog refuses to save one, so
 * the story fills a column too.
 */
test.describe('Checklists', () => {
    /**
     * A checklist is a grid of people against things to tick, and ticking is the whole of using
     * one. The mark has to survive a reload, which is what says it reached the server.
     */
    test('a tick on a checklist is kept', async ({managerPage: page}) => {
        await page.goto('/station/checklist')

        await page.locator('main').getByText(/./).first().waitFor()
        await page.getByRole('link').filter({hasText: /./}).first().waitFor().catch(() => undefined)

        const entry = page.locator('main [class*="cursor-pointer"]').first()
        await entry.click()
        await page.waitForURL(/\/station\/checklist\/\d+/)

        const cell = page.getByRole('switch').first()
        await expect(cell).toBeVisible()
        const before = await cell.getAttribute('aria-checked')

        await cell.click()
        await page.reload()

        await expect(page.getByRole('switch').first()).not.toHaveAttribute('aria-checked', before ?? 'false')
    })

    test('a checklist is created', async ({managerPage: page}) => {
        const checklist = unique('Checkliste')

        await page.goto('/station/checklist')
        await page.getByRole('button', {name: 'Neue Checkliste'}).click()

        const fields = page.getByRole('textbox')
        await fields.first().fill(checklist)
        await fields.nth(2).fill('Erledigt')

        await page.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(checklist)).toBeVisible()
    })
})
