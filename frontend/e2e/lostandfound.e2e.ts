/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/** The dialog's submit carries the same label as the button that opens it, so the story takes the
 * last of the two rather than the first. */
test.describe('Lost and found', () => {
    test('a found item is reported and appears in the list', async ({managerPage: page}) => {
        const item = unique('Fundstueck')

        await page.goto('/station/lost-and-found')
        await page.getByRole('button', {name: 'Fundgegenstand melden'}).click()

        await page.getByRole('textbox').first().fill(item)
        await page.getByRole('button', {name: 'Fundgegenstand melden'}).last().click()

        await expect(page.getByText(item).first()).toBeVisible()
    })
})
