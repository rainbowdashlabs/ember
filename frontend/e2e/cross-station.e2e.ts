/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

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
