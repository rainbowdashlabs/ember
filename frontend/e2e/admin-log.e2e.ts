/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/**
 * Reading the instance log from inside the application.
 *
 * The stories hold two things in place. That the page says plainly when nothing is being stored,
 * because an empty log and a switched-off log look identical and only one of them is a problem. And
 * that the log is the instance administration's alone: it names people the way any log does.
 */
const LOG = '/admin/monitoring/log'
const SETTINGS = '/admin/settings'

test.describe('Application log', () => {
    test('the log page offers search and every level', async ({adminPage: page}) => {
        await page.goto(LOG)

        await expect(page.getByLabel('In Nachricht oder Logger suchen')).toBeVisible()
        for (const level of ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE']) {
            await expect(page.getByLabel(level, {exact: true})).toBeVisible()
        }
    })

    /**
     * An empty log and a log nobody is writing look the same. Saying which is the difference between
     * a quiet instance and a setting somebody forgot.
     */
    test('a log that is not being stored says so rather than looking empty', async ({adminPage: page}) => {
        await page.goto(LOG)

        await expect(page.getByText(/nicht in der Datenbank gespeichert/)).toBeVisible()
    })

    test('what is kept and for how long is set under the settings', async ({adminPage: page}) => {
        await page.goto(SETTINGS)

        await expect(page.getByLabel('Log in der Datenbank speichern')).toBeVisible()
        await expect(page.getByLabel('Aufbewahrung in Tagen')).toBeVisible()
        await expect(page.getByLabel('Ab welcher Stufe')).toBeVisible()
    })

    test('a station manager reaches none of it', async ({managerPage: page}) => {
        await page.goto(LOG)

        await expect(page.getByLabel('In Nachricht oder Logger suchen')).toHaveCount(0)
    })
})
