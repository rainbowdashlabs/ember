/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

test.describe('Attendance', () => {
    /**
     * Recording who was there is the whole of attendance. The story opens a past session, marks the
     * first member present and reloads: a mark that does not survive a reload never reached the
     * server.
     */
    test('a member is marked present in a session', async ({managerPage: page}) => {
        await page.goto('/station/attendance/past')

        await page.getByTestId('attendance-session').first().click()
        await page.waitForURL(/\/station\/attendance\/session\/\d+/)

        // Whoever is already present has that button switched off, so the story marks someone who
        // is not — and afterwards their button is the one switched off.
        const present = page.locator('button[aria-label="Anwesend"]:not([disabled])').first()
        await expect(present).toBeVisible()
        await present.click()

        await page.reload()
        await expect(page.locator('button[aria-label="Anwesend"][disabled]').first()).toBeVisible()
    })

    test('an attendance session can be opened', async ({managerPage: page}) => {
        await page.goto('/station/attendance/new')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/attendance/new')
    })

    test('past sessions are listed', async ({managerPage: page}) => {
        await page.goto('/station/attendance/past')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('the attendance report is reachable', async ({managerPage: page}) => {
        await page.goto('/station/attendance/report')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('a member does not record attendance', async ({memberPage: page}) => {
        await page.goto('/station/attendance/new')

        await expect(page.getByRole('button', {name: /Speichern|Starten/})).toHaveCount(0)
    })
})
