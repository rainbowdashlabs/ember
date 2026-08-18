/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * A test sheet of the story's own. The dialog's submit repeats the label of the button that opened
 * it, as several others in the application do, so the story takes the one on the dialog.
 */
async function createSheet(page: Page): Promise<string> {
    const sheet = unique('Pruefungsbogen')

    await page.goto('/station/protocols')
    await page.getByRole('button', {name: 'Neuer Prüfungsbogen'}).click()
    await page.getByRole('textbox').first().fill(sheet)
    await page.getByRole('button', {name: 'Neuer Prüfungsbogen'}).last().click()

    await expect(page.getByText(sheet).first()).toBeVisible()
    return sheet
}

/**
 * A run of that sheet, left open on the run's own page.
 *
 * A run with nobody in it examines nobody, so a whole member type goes into it: the dialog keeps its
 * submit disabled until the sheet and a name are both there.
 */
async function createRun(page: Page, sheet: string): Promise<string> {
    const run = unique('Lauf')

    await page.goto('/station/protocols/runs')
    await page.getByRole('button', {name: 'Neuer Prüfungslauf'}).click()
    await page.getByRole('combobox').first().selectOption({label: sheet})
    await page.getByPlaceholder('Name des Laufs').fill(run)

    await page.getByRole('button', {name: 'Mitgliedstyp'}).click()
    await page.getByRole('button', {name: 'Mitglied', exact: true}).click()
    await page.keyboard.press('Escape')

    await page.getByRole('button', {name: 'Neuer Prüfungslauf'}).last().click()
    await expect(page.getByText(run).first()).toBeVisible()

    await page.getByText(run).first().click()
    await page.waitForURL(/\/station\/protocols\/runs\/\d+/)
    return run
}

test.describe('Protocols', () => {
    test('a test sheet is created', async ({managerPage: page}) => {
        await createSheet(page)
    })

    /** A test sheet exists to be run: the run is where people are actually examined. */
    test('a run is opened for a test sheet', async ({managerPage: page}) => {
        await createRun(page, await createSheet(page))
    })

    test('a member of a run is opened for grading', async ({managerPage: page}) => {
        await createRun(page, await createSheet(page))

        await page.getByRole('button', {name: 'Prüfen'}).first().click()
        await page.waitForURL(/\/grade\/\d+/)
    })

    /**
     * A run ends on paper: the examination has to be filed, and what is filed is the table of who
     * was examined. The story takes the download and checks it carries bytes, since an empty PDF
     * looks like a successful one until somebody opens it.
     */
    test('the evaluation of a run downloads as a PDF', async ({managerPage: page}) => {
        await createRun(page, await createSheet(page))

        await page.goto(`${page.url()}/evaluation`)

        const download = page.waitForEvent('download')
        await page.getByRole('button', {name: 'Tabelle als PDF'}).click()

        expect((await download).suggestedFilename()).toMatch(/\.pdf$/)
    })

    test('the protocol list offers a new test sheet to whoever runs them', async ({managerPage: page}) => {
        await page.goto('/station/protocols')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neuer Prüfungsbogen'})).toBeVisible()
    })

    test('a member is not offered a new test sheet', async ({memberPage: page}) => {
        await page.goto('/station/protocols')

        await expect(page.getByRole('button', {name: 'Neuer Prüfungsbogen'})).toHaveCount(0)
    })
})
