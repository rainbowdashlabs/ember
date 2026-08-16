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
